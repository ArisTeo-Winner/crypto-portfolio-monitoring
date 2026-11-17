package com.mx.cryptomonitor.unit.marketdata.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mx.cryptomonitor.marketdata.application.port.out.LastKnownQuotePort;
import com.mx.cryptomonitor.marketdata.application.port.out.OfficialClosePort;
import com.mx.cryptomonitor.marketdata.application.port.out.StockQuoteProvider;
import com.mx.cryptomonitor.marketdata.application.service.ClosedMarketQuotePolicy;
import com.mx.cryptomonitor.marketdata.application.service.StockQuoteOrchestrator;
import com.mx.cryptomonitor.marketdata.domain.exception.AlphaVantageInvalidSymbolException;
import com.mx.cryptomonitor.marketdata.domain.exception.ExternalProviderRateLimitException;
import com.mx.cryptomonitor.marketdata.domain.model.ClosedWindow;
import com.mx.cryptomonitor.marketdata.domain.model.HeldQuoteSource;
import com.mx.cryptomonitor.marketdata.domain.model.Market;
import com.mx.cryptomonitor.marketdata.domain.model.StockQuote;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/** Flujo de {@code getLatest} con último precio conocido y retención de cierre (ADR-0033). */
@ExtendWith(MockitoExtension.class)
class StockQuoteOrchestratorRetentionTest {

  private static final Instant NOW = Instant.parse("2026-11-14T15:00:00Z");
  private static final Instant MONDAY_OPEN = Instant.parse("2026-11-16T14:30:00Z");
  private static final LocalDate FRIDAY = LocalDate.of(2026, 11, 13);
  private static final BigDecimal CHAIN_PRICE = new BigDecimal("210.15");

  @Mock private StockQuoteProvider provider;
  @Mock private LastKnownQuotePort quoteStore;
  @Mock private ClosedMarketQuotePolicy policy;
  @Mock private OfficialClosePort officialClose;

  private SimpleMeterRegistry meterRegistry;
  private StockQuoteOrchestrator orchestrator;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    orchestrator =
        new StockQuoteOrchestrator(
            List.of(provider),
            quoteStore,
            policy,
            officialClose,
            meterRegistry,
            Clock.fixed(NOW, ZoneOffset.UTC));
    lenient().when(provider.providerName()).thenReturn("eodhd");
    lenient().when(quoteStore.findHeld("AAPL")).thenReturn(Optional.empty());
    lenient().when(policy.closedWindow(Market.US_EQUITY)).thenReturn(Optional.empty());
  }

  @Test
  void heldCloseIsReturnedWithoutTouchingAnyExternalSource() {
    when(quoteStore.findHeld("AAPL"))
        .thenReturn(Optional.of(new StockQuote(new BigDecimal("356.27"), NOW, "eodhd", FRIDAY)));

    assertThat(orchestrator.getLatest("aapl")).contains(new BigDecimal("356.27"));

    verifyNoInteractions(provider, policy, officialClose);
    assertThat(served("held_market_closed")).isEqualTo(1.0);
  }

  @Test
  void openMarketUsesTheChainAndRemembersThePriceWithoutHolding() {
    when(provider.getLatest("AAPL")).thenReturn(Optional.of(CHAIN_PRICE));

    assertThat(orchestrator.getLatest("AAPL")).contains(CHAIN_PRICE);

    verify(quoteStore).saveLast("AAPL", new StockQuote(CHAIN_PRICE, NOW, "eodhd", null));
    verify(quoteStore, never()).hold(any(), any(), any(), any());
    verifyNoInteractions(officialClose);
    assertThat(served("provider")).isEqualTo(1.0);
  }

  @Test
  void officialCloseOfTheExpectedSessionIsHeldUntilTheBell() {
    StockQuote official =
        new StockQuote(new BigDecimal("356.26999"), NOW, "twelvedata-eod", FRIDAY);
    when(policy.closedWindow(Market.US_EQUITY))
        .thenReturn(Optional.of(new ClosedWindow(FRIDAY, MONDAY_OPEN, false)));
    when(officialClose.find("AAPL")).thenReturn(Optional.of(official));

    assertThat(orchestrator.getLatest("AAPL")).contains(new BigDecimal("356.26999"));

    verify(quoteStore).hold("AAPL", official, HeldQuoteSource.OFFICIAL_EOD, MONDAY_OPEN);
    verify(quoteStore).saveLast("AAPL", official);
    verify(provider, never()).getLatest(any());
    assertThat(served("official_close")).isEqualTo(1.0);
  }

  @Test
  void officialCloseOfAPreviousSessionIsNotHeld() {
    when(policy.closedWindow(Market.US_EQUITY))
        .thenReturn(Optional.of(new ClosedWindow(FRIDAY, MONDAY_OPEN, false)));
    when(officialClose.find("AAPL"))
        .thenReturn(
            Optional.of(
                new StockQuote(
                    new BigDecimal("350.00"), NOW, "twelvedata-eod", FRIDAY.minusDays(1))));
    when(provider.getLatest("AAPL")).thenReturn(Optional.of(CHAIN_PRICE));

    assertThat(orchestrator.getLatest("AAPL")).contains(CHAIN_PRICE);

    verify(quoteStore, never()).hold(any(), any(), any(), any());
  }

  @Test
  void withoutConfirmationTheChainPriceIsHeldOnceTheWindowIsSettled() {
    when(policy.closedWindow(Market.US_EQUITY))
        .thenReturn(Optional.of(new ClosedWindow(FRIDAY, MONDAY_OPEN, true)));
    when(officialClose.find("AAPL")).thenReturn(Optional.empty());
    when(provider.getLatest("AAPL")).thenReturn(Optional.of(CHAIN_PRICE));

    assertThat(orchestrator.getLatest("AAPL")).contains(CHAIN_PRICE);

    verify(quoteStore)
        .hold(
            "AAPL",
            new StockQuote(CHAIN_PRICE, NOW, "eodhd", null),
            HeldQuoteSource.SETTLED_CHAIN,
            MONDAY_OPEN);
  }

  @Test
  void totalFailureServesTheLastKnownPriceAndNeverHoldsIt() {
    when(policy.closedWindow(Market.US_EQUITY))
        .thenReturn(Optional.of(new ClosedWindow(FRIDAY, MONDAY_OPEN, true)));
    when(officialClose.find("AAPL")).thenReturn(Optional.empty());
    when(provider.getLatest("AAPL"))
        .thenThrow(new ExternalProviderRateLimitException("eodhd", "Daily quota exceeded"));
    when(quoteStore.findLast("AAPL"))
        .thenReturn(
            Optional.of(
                new StockQuote(new BigDecimal("205.00"), NOW.minusSeconds(3600), "eodhd", null)));

    assertThat(orchestrator.getLatest("AAPL")).contains(new BigDecimal("205.00"));

    verify(quoteStore, never()).hold(any(), any(), any(), any());
    verify(quoteStore, never()).saveLast(any(), any());
    assertThat(served("last_known_fallback")).isEqualTo(1.0);
  }

  @Test
  void totalFailureWithoutLastKnownPriceRethrowsTheProviderFailure() {
    ExternalProviderRateLimitException failure =
        new ExternalProviderRateLimitException("eodhd", "Daily quota exceeded");
    when(provider.getLatest("AAPL")).thenThrow(failure);
    when(quoteStore.findLast("AAPL")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> orchestrator.getLatest("AAPL")).isSameAs(failure);
  }

  @Test
  void providersWithoutDataFallBackToTheLastKnownPrice() {
    when(provider.getLatest("AAPL")).thenReturn(Optional.empty());
    when(quoteStore.findLast("AAPL"))
        .thenReturn(Optional.of(new StockQuote(new BigDecimal("205.00"), NOW, "eodhd", null)));

    assertThat(orchestrator.getLatest("AAPL")).contains(new BigDecimal("205.00"));
  }

  @Test
  void invalidSymbolNeverUsesTheLastKnownPrice() {
    when(provider.getLatest("BAD")).thenThrow(new AlphaVantageInvalidSymbolException("Invalid"));
    lenient().when(quoteStore.findHeld("BAD")).thenReturn(Optional.empty());

    assertThatThrownBy(() -> orchestrator.getLatest("BAD"))
        .isInstanceOf(AlphaVantageInvalidSymbolException.class);

    verify(quoteStore, never()).findLast(any());
  }

  @Test
  void calendarFailureDoesNotBreakTheQuote() {
    when(policy.closedWindow(Market.US_EQUITY)).thenThrow(new IllegalStateException("db down"));
    when(provider.getLatest("AAPL")).thenReturn(Optional.of(CHAIN_PRICE));

    assertThat(orchestrator.getLatest("AAPL")).contains(CHAIN_PRICE);

    verify(quoteStore, never()).hold(any(), any(), any(), any());
  }

  private double served(String source) {
    return meterRegistry.get("marketdata.quote.served").tag("source", source).counter().count();
  }
}
