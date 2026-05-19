package com.mx.cryptomonitor.unit.portfolio.infrastructure.outbound.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.mx.cryptomonitor.portfolio.application.port.out.PriceHistoryCachePort;
import com.mx.cryptomonitor.portfolio.application.port.out.PriceHistoryCachePort.PriceHistoryPoint;
import com.mx.cryptomonitor.portfolio.application.service.HoldingsHistoryRange;
import com.mx.cryptomonitor.portfolio.domain.model.AssetType;
import com.mx.cryptomonitor.portfolio.domain.model.PricePoint;
import com.mx.cryptomonitor.portfolio.infrastructure.outbound.marketdata.CachedMarketPriceHistoryAdapter;
import com.mx.cryptomonitor.portfolio.infrastructure.outbound.marketdata.MarketPriceHistoryProvider;

class CachedMarketPriceHistoryAdapterCacheBehaviorTest {

  private static final AssetType CRYPTO = AssetType.CRYPTO;
  private static final String SYMBOL = "HYPE";
  private static final String RANGE = "30d";
  private static final String RANGE_KEY = HoldingsHistoryRange.parse(RANGE).value();

  private static final List<PriceHistoryPoint> CACHED_POINTS =
      List.of(
          new PriceHistoryPoint(Instant.parse("2026-01-01T00:00:00Z"), new BigDecimal("10.00")),
          new PriceHistoryPoint(Instant.parse("2026-01-02T00:00:00Z"), new BigDecimal("11.50")));

  private PriceHistoryCachePort cache;
  private MarketPriceHistoryProvider provider;

  @BeforeEach
  void setUp() {
    cache = mock(PriceHistoryCachePort.class);
    provider = mock(MarketPriceHistoryProvider.class);
    when(provider.supports(CRYPTO)).thenReturn(true);
  }

  // -------------------------------------------------------------------------
  // Test A: cache HIT
  // -------------------------------------------------------------------------

  @Test
  void cacheHit_returnsCached_andDoesNotCallProviders() {
    when(cache.getPriceHistory(CRYPTO, SYMBOL, RANGE_KEY)).thenReturn(CACHED_POINTS);

    CachedMarketPriceHistoryAdapter adapter =
        new CachedMarketPriceHistoryAdapter(cache, List.of(provider));

    List<PricePoint> result = adapter.getPriceHistory(CRYPTO, SYMBOL, RANGE);

    // Result matches cached data (timestamp + price)
    assertThat(result)
        .extracting(PricePoint::time)
        .containsExactly(CACHED_POINTS.get(0).time(), CACHED_POINTS.get(1).time());
    assertThat(result)
        .extracting(PricePoint::price)
        .containsExactly(CACHED_POINTS.get(0).price(), CACHED_POINTS.get(1).price());

    // Cache hit — no lock, no fetch, no store
    verify(cache, never()).acquireLoadLock(any(), any(), any(), any());
    verify(cache, never()).storePriceHistory(any(), any(), any(), any(), any());
    verify(provider, never()).fetchPriceHistory(any(), any(), (HoldingsHistoryRange) any());
  }

  // -------------------------------------------------------------------------
  // Test B: lock NOT acquired — another node is loading; this thread polls
  // -------------------------------------------------------------------------

  @Test
  void lockNotAcquired_waitsForCache_thenReturns_withoutCallingProviders() {
    // First call (before lock attempt) → miss; subsequent poll calls → miss, miss, HIT
    when(cache.getPriceHistory(CRYPTO, SYMBOL, RANGE_KEY))
        .thenReturn(List.of())          // initial check → miss
        .thenReturn(List.of())          // 1st poll iteration → still empty
        .thenReturn(CACHED_POINTS);     // 2nd poll iteration → data arrived

    // Lock is held by another node
    when(cache.acquireLoadLock(eq(CRYPTO), eq(SYMBOL), eq(RANGE_KEY), any()))
        .thenReturn(false);

    CachedMarketPriceHistoryAdapter adapter =
        new CachedMarketPriceHistoryAdapter(cache, List.of(provider));

    List<PricePoint> result = adapter.getPriceHistory(CRYPTO, SYMBOL, RANGE);

    // Returns whatever the cache eventually produced
    assertThat(result)
        .extracting(PricePoint::time)
        .containsExactly(CACHED_POINTS.get(0).time(), CACHED_POINTS.get(1).time());

    // Providers are never touched — this thread never fetched live data
    verify(provider, never()).fetchPriceHistory(any(), any(), (HoldingsHistoryRange) any());

    // Lock was never acquired, so it must never be released
    verify(cache, never()).releaseLoadLock(any(), any(), any());

    // Cache was polled more than once (initial check + at least one wait iteration)
    Mockito.verify(cache, Mockito.atLeast(2)).getPriceHistory(CRYPTO, SYMBOL, RANGE_KEY);
  }
}
