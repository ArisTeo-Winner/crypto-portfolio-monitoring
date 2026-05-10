package com.mx.cryptomonitor.unit.portfolio.infrastructure.outbound.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.mx.cryptomonitor.portfolio.application.port.out.PriceHistoryCachePort;
import com.mx.cryptomonitor.portfolio.application.service.HoldingsHistoryRange;
import com.mx.cryptomonitor.portfolio.domain.model.AssetType;
import com.mx.cryptomonitor.portfolio.domain.model.PricePoint;
import com.mx.cryptomonitor.portfolio.infrastructure.outbound.marketdata.CachedMarketPriceHistoryAdapter;
import com.mx.cryptomonitor.portfolio.infrastructure.outbound.marketdata.MarketPriceHistoryProvider;

class CachedMarketPriceHistoryAdapterTest {

  private final PriceHistoryCachePort cache = org.mockito.Mockito.mock(PriceHistoryCachePort.class);
  private final MarketPriceHistoryProvider provider =
      org.mockito.Mockito.mock(MarketPriceHistoryProvider.class);
  private final CachedMarketPriceHistoryAdapter adapter =
      new CachedMarketPriceHistoryAdapter(cache, List.of(provider));

  @Test
  void cacheHitReturnsSharedRedisSeriesWithoutCallingProvider() {
    Instant time = Instant.parse("2026-01-01T00:00:00Z");
    when(cache.getPriceHistory(AssetType.CRYPTO, "SOL", "180d"))
        .thenReturn(List.of(new PriceHistoryCachePort.PriceHistoryPoint(time, new BigDecimal("101.25"))));

    List<PricePoint> result = adapter.getPriceHistory(AssetType.CRYPTO, "SOL", "180d");

    assertThat(result).containsExactly(new PricePoint(time, new BigDecimal("101.25")));
    verify(provider, never()).fetchPriceHistory(any(), any(), any());
  }

  @Test
  void cacheMissFetchesProviderAndStoresSharedSeriesWithRangeTtl() {
    Instant time = Instant.parse("2026-01-01T00:00:00Z");
    PricePoint point = new PricePoint(time, new BigDecimal("98.50"));
    when(cache.getPriceHistory(AssetType.CRYPTO, "SOL", "180d")).thenReturn(List.of());
    when(cache.acquireLoadLock(eq(AssetType.CRYPTO), eq("SOL"), eq("180d"), any(Duration.class)))
        .thenReturn(true);
    when(provider.supports(AssetType.CRYPTO)).thenReturn(true);
    when(provider.fetchPriceHistory(eq(AssetType.CRYPTO), eq("SOL"), any(HoldingsHistoryRange.class)))
        .thenReturn(List.of(point));

    List<PricePoint> result = adapter.getPriceHistory(AssetType.CRYPTO, "SOL", "180d");

    assertThat(result).containsExactly(point);
    verify(cache)
        .storePriceHistory(
            eq(AssetType.CRYPTO),
            eq("SOL"),
            eq("180d"),
            eq(List.of(new PriceHistoryCachePort.PriceHistoryPoint(time, new BigDecimal("98.50")))),
            eq(Duration.ofHours(6)));
    verify(cache).releaseLoadLock(AssetType.CRYPTO, "SOL", "180d");
  }
}
