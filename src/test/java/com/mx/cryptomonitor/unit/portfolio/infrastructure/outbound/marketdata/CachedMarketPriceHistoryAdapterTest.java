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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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

  @Test
  void concurrentCacheMissWaitsForSharedLoadAndReturnsPopulatedSeriesToAllCallers()
      throws Exception {
    FakePriceHistoryCache fakeCache = new FakePriceHistoryCache();
    CountDownLatch providerEntered = new CountDownLatch(1);
    CountDownLatch releaseProvider = new CountDownLatch(1);
    AtomicInteger providerCalls = new AtomicInteger();
    Instant time = Instant.parse("2026-01-01T00:00:00Z");
    PricePoint point = new PricePoint(time, new BigDecimal("100.00"));
    MarketPriceHistoryProvider slowProvider =
        new MarketPriceHistoryProvider() {
          @Override
          public boolean supports(AssetType assetType) {
            return assetType == AssetType.CRYPTO;
          }

          @Override
          public List<PricePoint> fetchPriceHistory(
              AssetType assetType, String symbol, HoldingsHistoryRange range) {
            providerCalls.incrementAndGet();
            providerEntered.countDown();
            try {
              releaseProvider.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException ex) {
              Thread.currentThread().interrupt();
            }
            return List.of(point);
          }
        };
    CachedMarketPriceHistoryAdapter concurrentAdapter =
        new CachedMarketPriceHistoryAdapter(fakeCache, List.of(slowProvider));

    var executor = Executors.newFixedThreadPool(20);
    List<java.util.concurrent.Future<List<PricePoint>>> futures = new ArrayList<>();
    futures.add(
        executor.submit(() -> concurrentAdapter.getPriceHistory(AssetType.CRYPTO, "BTC", "30d")));
    assertThat(providerEntered.await(1, TimeUnit.SECONDS)).isTrue();
    for (int i = 0; i < 19; i++) {
      futures.add(
          executor.submit(() -> concurrentAdapter.getPriceHistory(AssetType.CRYPTO, "BTC", "30d")));
    }

    releaseProvider.countDown();
    executor.shutdown();
    assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

    for (java.util.concurrent.Future<List<PricePoint>> future : futures) {
      assertThat(future.get()).containsExactly(point);
    }
    assertThat(providerCalls.get()).isEqualTo(1);
  }

  private static final class FakePriceHistoryCache implements PriceHistoryCachePort {
    private final Map<String, List<PriceHistoryPoint>> values = new ConcurrentHashMap<>();
    private final Set<String> locks = ConcurrentHashMap.newKeySet();

    @Override
    public void storePriceHistory(
        AssetType assetType,
        String symbol,
        String range,
        List<PriceHistoryPoint> points,
        Duration ttl) {
      values.put(key(assetType, symbol, range), List.copyOf(points));
    }

    @Override
    public List<PriceHistoryPoint> getPriceHistory(AssetType assetType, String symbol, String range) {
      return values.getOrDefault(key(assetType, symbol, range), List.of());
    }

    @Override
    public boolean acquireLoadLock(AssetType assetType, String symbol, String range, Duration ttl) {
      return locks.add(key(assetType, symbol, range));
    }

    @Override
    public void releaseLoadLock(AssetType assetType, String symbol, String range) {
      locks.remove(key(assetType, symbol, range));
    }

    private String key(AssetType assetType, String symbol, String range) {
      return assetType + ":" + symbol + ":" + range;
    }
  }
}
