package com.mx.cryptomonitor.unit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mx.cryptomonitor.marketdata.application.port.out.StockQuoteProvider;
import com.mx.cryptomonitor.marketdata.application.service.StockQuoteOrchestrator;
import com.mx.cryptomonitor.marketdata.domain.exception.AlphaVantageInvalidSymbolException;
import com.mx.cryptomonitor.marketdata.domain.exception.AlphaVantageServerException;
import com.mx.cryptomonitor.marketdata.domain.exception.ExternalProviderRateLimitException;

@ExtendWith(MockitoExtension.class)
class StockQuoteOrchestratorTest {

  @Mock private StockQuoteProvider primaryProvider;
  @Mock private StockQuoteProvider fallbackProvider;

  @Test
  void getLatestShouldReturnFirstProviderResultWhenAvailable() {
    when(primaryProvider.getLatest("AAPL")).thenReturn(Optional.of(new BigDecimal("210.15")));

    StockQuoteOrchestrator orchestrator =
        new StockQuoteOrchestrator(List.of(primaryProvider, fallbackProvider));

    Optional<BigDecimal> result = orchestrator.getLatest("AAPL");

    assertThat(result).contains(new BigDecimal("210.15"));
    verify(primaryProvider).getLatest("AAPL");
    verifyNoInteractions(fallbackProvider);
  }

  @Test
  void getLatestShouldFallbackWhenPrimaryProviderIsRateLimited() {
    when(primaryProvider.providerName()).thenReturn("alphavantage");
    when(fallbackProvider.providerName()).thenReturn("provider-b");
    when(primaryProvider.getLatest("AAPL"))
        .thenThrow(new ExternalProviderRateLimitException("alphavantage", "Daily quota exceeded"));
    when(fallbackProvider.getLatest("AAPL")).thenReturn(Optional.of(new BigDecimal("212.30")));

    StockQuoteOrchestrator orchestrator =
        new StockQuoteOrchestrator(List.of(primaryProvider, fallbackProvider));

    Optional<BigDecimal> result = orchestrator.getLatest("AAPL");

    assertThat(result).contains(new BigDecimal("212.30"));
    verify(primaryProvider).getLatest("AAPL");
    verify(fallbackProvider).getLatest("AAPL");
  }

  @Test
  void getLatestShouldStopFallbackWhenProviderReportsInvalidSymbol() {
    when(primaryProvider.providerName()).thenReturn("alphavantage");
    when(primaryProvider.getLatest("BAD"))
        .thenThrow(new AlphaVantageInvalidSymbolException("Invalid symbol"));

    StockQuoteOrchestrator orchestrator =
        new StockQuoteOrchestrator(List.of(primaryProvider, fallbackProvider));

    assertThatThrownBy(() -> orchestrator.getLatest("BAD"))
        .isInstanceOf(AlphaVantageInvalidSymbolException.class)
        .hasMessageContaining("Invalid symbol");

    verify(primaryProvider).getLatest("BAD");
    verifyNoInteractions(fallbackProvider);
  }

  @Test
  void getLatestShouldPropagateFirstRecoverableFailureWhenAllProvidersFail() {
    ExternalProviderRateLimitException firstFailure =
        new ExternalProviderRateLimitException("alphavantage", "Daily quota exceeded");

    when(primaryProvider.providerName()).thenReturn("alphavantage");
    when(fallbackProvider.providerName()).thenReturn("provider-b");
    when(primaryProvider.getLatest("AAPL")).thenThrow(firstFailure);
    when(fallbackProvider.getLatest("AAPL"))
        .thenThrow(new AlphaVantageServerException("Temporary upstream error"));

    StockQuoteOrchestrator orchestrator =
        new StockQuoteOrchestrator(List.of(primaryProvider, fallbackProvider));

    assertThatThrownBy(() -> orchestrator.getLatest("AAPL")).isSameAs(firstFailure);

    verify(primaryProvider).getLatest("AAPL");
    verify(fallbackProvider).getLatest("AAPL");
  }

  @Test
  void getHistoricalShouldFallbackToNextProviderWhenPrimaryReturnsEmpty() {
    LocalDate date = LocalDate.of(2025, 11, 25);
    when(primaryProvider.providerName()).thenReturn("alphavantage");
    when(primaryProvider.getHistorical("AAPL", date)).thenReturn(Optional.empty());
    when(fallbackProvider.getHistorical("AAPL", date))
        .thenReturn(Optional.of(new BigDecimal("198.44")));

    StockQuoteOrchestrator orchestrator =
        new StockQuoteOrchestrator(List.of(primaryProvider, fallbackProvider));

    Optional<BigDecimal> result = orchestrator.getHistorical("AAPL", date);

    assertThat(result).contains(new BigDecimal("198.44"));
    verify(primaryProvider).getHistorical("AAPL", date);
    verify(fallbackProvider).getHistorical("AAPL", date);
  }
}
