package com.mx.cryptomonitor.unit.portfolio.infrastructure.outbound.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.mx.cryptomonitor.portfolio.application.port.out.PriceHistoryCachePort;
import com.mx.cryptomonitor.portfolio.application.port.out.PriceHistoryCachePort.PriceHistoryPoint;
import com.mx.cryptomonitor.portfolio.application.service.HoldingsHistoryRange;
import com.mx.cryptomonitor.portfolio.domain.exception.UnknownAssetSymbolException;
import com.mx.cryptomonitor.portfolio.domain.model.AssetType;
import com.mx.cryptomonitor.portfolio.domain.model.ChartResolution;
import com.mx.cryptomonitor.portfolio.domain.model.PricePoint;
import com.mx.cryptomonitor.portfolio.infrastructure.outbound.marketdata.CachedMarketPriceHistoryAdapter;
import com.mx.cryptomonitor.portfolio.infrastructure.outbound.marketdata.MarketPriceHistoryProvider;

class CachedMarketPriceHistoryAdapterFallbackTest {

  private static final AssetType CRYPTO = AssetType.CRYPTO;
  private static final String SYMBOL = "HYPE";
  private static final String RANGE = "30d";
  private static final HoldingsHistoryRange PARSED_RANGE = HoldingsHistoryRange.parse(RANGE);

  private static final List<PricePoint> PROVIDER2_POINTS =
      List.of(
          new PricePoint(Instant.parse("2026-01-01T00:00:00Z"), new BigDecimal("10.00")),
          new PricePoint(Instant.parse("2026-01-02T00:00:00Z"), new BigDecimal("11.50")),
          new PricePoint(Instant.parse("2026-01-03T00:00:00Z"), new BigDecimal("12.75")));

  private PriceHistoryCachePort cache;
  private MarketPriceHistoryProvider provider1;
  private MarketPriceHistoryProvider provider2;
  private CachedMarketPriceHistoryAdapter adapter;

  @BeforeEach
  void setUp() {
    cache = mock(PriceHistoryCachePort.class);
    provider1 = mock(MarketPriceHistoryProvider.class);
    provider2 = mock(MarketPriceHistoryProvider.class);

    // Cache miss — forces live fetch
    when(cache.getPriceHistory(CRYPTO, SYMBOL, PARSED_RANGE.value())).thenReturn(List.of());
    // Lock acquired by this thread
    when(cache.acquireLoadLock(eq(CRYPTO), eq(SYMBOL), eq(PARSED_RANGE.value()), any()))
        .thenReturn(true);

    // provider1 (Binance-like): supports CRYPTO but doesn't know "HYPE"
    when(provider1.supports(CRYPTO)).thenReturn(true);
    when(provider1.fetchPriceHistory(eq(CRYPTO), eq(SYMBOL), eq(PARSED_RANGE)))
        .thenThrow(new UnknownAssetSymbolException("Binance symbol not found: HYPEUSDT"));

    // provider2 (CoinGecko-like): supports CRYPTO and returns data
    when(provider2.supports(CRYPTO)).thenReturn(true);
    when(provider2.fetchPriceHistory(eq(CRYPTO), eq(SYMBOL), eq(PARSED_RANGE)))
        .thenReturn(PROVIDER2_POINTS);

    adapter = new CachedMarketPriceHistoryAdapter(cache, List.of(provider1, provider2));
  }

  @Test
  void shouldFallbackToProvider2WhenProvider1ThrowsUnknownAssetSymbol() {
    List<PricePoint> result = adapter.getPriceHistory(CRYPTO, SYMBOL, RANGE);

    assertThat(result).containsExactlyInAnyOrderElementsOf(PROVIDER2_POINTS);
  }

  @Test
  void shouldInvokeProvider1BeforeProvider2() {
    adapter.getPriceHistory(CRYPTO, SYMBOL, RANGE);

    InOrder order = inOrder(provider1, provider2);
    order.verify(provider1).fetchPriceHistory(CRYPTO, SYMBOL, PARSED_RANGE);
    order.verify(provider2).fetchPriceHistory(CRYPTO, SYMBOL, PARSED_RANGE);
  }

  @Test
  void shouldStoreProvider2ResultsInCacheWithCorrectTtl() {
    adapter.getPriceHistory(CRYPTO, SYMBOL, RANGE);

    List<PriceHistoryPoint> expectedCachePoints =
        PROVIDER2_POINTS.stream().map(p -> new PriceHistoryPoint(p.time(), p.price())).toList();

    verify(cache)
        .storePriceHistory(
            CRYPTO, SYMBOL, PARSED_RANGE.value(), expectedCachePoints, PARSED_RANGE.ttl());
  }

  @Test
  void shouldReleaseLoadLockEvenAfterSuccessfulFallback() {
    adapter.getPriceHistory(CRYPTO, SYMBOL, RANGE);

    verify(cache).releaseLoadLock(CRYPTO, SYMBOL, PARSED_RANGE.value());
  }

  @Test
  void shouldReturnCachedDataWithoutCallingAnyProvider() {
    List<PriceHistoryPoint> cachedPoints =
        PROVIDER2_POINTS.stream().map(p -> new PriceHistoryPoint(p.time(), p.price())).toList();
    when(cache.getPriceHistory(CRYPTO, SYMBOL, PARSED_RANGE.value())).thenReturn(cachedPoints);

    List<PricePoint> result = adapter.getPriceHistory(CRYPTO, SYMBOL, RANGE);

    assertThat(result).hasSize(PROVIDER2_POINTS.size());
    verify(provider1, never()).fetchPriceHistory(any(), any(), (HoldingsHistoryRange) any());
    verify(provider2, never()).fetchPriceHistory(any(), any(), (HoldingsHistoryRange) any());
  }

  @Test
  void shouldFallbackToProvider2WhenDynamicChartResolutionProvider1DoesNotKnowSymbol() {
    ChartResolution chartResolution =
        new ChartResolution(
            Instant.parse("2026-05-17T00:00:00Z"),
            Instant.parse("2026-05-20T12:00:00Z"),
            Duration.ofHours(1),
            "1h",
            84);
    String dynamicKey = dynamicKey(chartResolution);
    when(cache.getPriceHistory(CRYPTO, SYMBOL, dynamicKey)).thenReturn(List.of());
    when(cache.acquireLoadLock(eq(CRYPTO), eq(SYMBOL), eq(dynamicKey), any())).thenReturn(true);
    when(provider1.fetchPriceHistory(eq(CRYPTO), eq(SYMBOL), eq(chartResolution)))
        .thenThrow(new UnknownAssetSymbolException("Binance symbol not found: HYPEUSDT"));
    when(provider2.fetchPriceHistory(eq(CRYPTO), eq(SYMBOL), eq(chartResolution)))
        .thenReturn(PROVIDER2_POINTS);

    List<PricePoint> result = adapter.getPriceHistory(CRYPTO, SYMBOL, chartResolution);

    assertThat(result).containsExactlyInAnyOrderElementsOf(PROVIDER2_POINTS);
    InOrder order = inOrder(provider1, provider2);
    order.verify(provider1).fetchPriceHistory(CRYPTO, SYMBOL, chartResolution);
    order.verify(provider2).fetchPriceHistory(CRYPTO, SYMBOL, chartResolution);
    verify(cache).releaseLoadLock(CRYPTO, SYMBOL, dynamicKey);
  }

  private String dynamicKey(ChartResolution chartResolution) {
    return "dyn:"
        + alignToDay(chartResolution.start().getEpochSecond())
        + ":"
        + alignToDay(chartResolution.end().getEpochSecond())
        + ":"
        + chartResolution.providerIntervalCode();
  }

  private long alignToDay(long epochSeconds) {
    return epochSeconds - (epochSeconds % 86400L);
  }
}
