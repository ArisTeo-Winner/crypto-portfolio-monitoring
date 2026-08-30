package com.mx.cryptomonitor.portfolio.infrastructure.outbound.marketdata;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import com.mx.cryptomonitor.portfolio.application.port.out.MarketPriceHistoryPort;
import com.mx.cryptomonitor.portfolio.application.port.out.PriceHistoryCachePort;
import com.mx.cryptomonitor.portfolio.application.service.HoldingsHistoryRange;
import com.mx.cryptomonitor.portfolio.domain.exception.MarketDataRateLimitException;
import com.mx.cryptomonitor.portfolio.domain.exception.MarketDataServerException;
import com.mx.cryptomonitor.portfolio.domain.exception.UnknownAssetSymbolException;
import com.mx.cryptomonitor.portfolio.domain.model.AssetType;
import com.mx.cryptomonitor.portfolio.domain.model.ChartResolution;
import com.mx.cryptomonitor.portfolio.domain.model.PricePoint;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class CachedMarketPriceHistoryAdapter implements MarketPriceHistoryPort {

  private static final Duration LOAD_LOCK_TTL = Duration.ofSeconds(30);
  private static final Duration CACHE_LOAD_WAIT_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration CACHE_LOAD_POLL_INTERVAL = Duration.ofMillis(50);

  private final PriceHistoryCachePort priceHistoryCachePort;
  private final List<MarketPriceHistoryProvider> providers;

  public CachedMarketPriceHistoryAdapter(
      PriceHistoryCachePort priceHistoryCachePort, List<MarketPriceHistoryProvider> providers) {
    this.priceHistoryCachePort = priceHistoryCachePort;
    this.providers = providers;
  }

  @Override
  public List<PricePoint> getPriceHistory(AssetType assetType, String symbol, String range) {
    HoldingsHistoryRange parsedRange = HoldingsHistoryRange.parse(range);
    List<PricePoint> cached = getCachedByKey(assetType, symbol, parsedRange.value());
    if (!cached.isEmpty()) {
      return cached;
    }
    if (!priceHistoryCachePort.acquireLoadLock(
        assetType, symbol, parsedRange.value(), LOAD_LOCK_TTL)) {
      return waitForCachedLoad(assetType, symbol, parsedRange.value());
    }
    try {
      List<PricePoint> prices = fetchWithFallback(assetType, symbol, parsedRange);
      storeIfNonEmpty(assetType, symbol, parsedRange.value(), prices, parsedRange.ttl());
      return prices;
    } finally {
      priceHistoryCachePort.releaseLoadLock(assetType, symbol, parsedRange.value());
    }
  }

  @Override
  public List<PricePoint> getPriceHistory(
      AssetType assetType, String symbol, ChartResolution chartResolution) {
    String cacheKey = buildDynamicKey(chartResolution);
    List<PricePoint> cached = getCachedByKey(assetType, symbol, cacheKey);
    if (!cached.isEmpty()) {
      return cached;
    }
    if (!priceHistoryCachePort.acquireLoadLock(assetType, symbol, cacheKey, LOAD_LOCK_TTL)) {
      return waitForCachedLoad(assetType, symbol, cacheKey);
    }
    try {
      List<PricePoint> prices = fetchWithFallback(assetType, symbol, chartResolution);
      storeIfNonEmpty(assetType, symbol, cacheKey, prices, deriveTtl(chartResolution));
      return prices;
    } finally {
      priceHistoryCachePort.releaseLoadLock(assetType, symbol, cacheKey);
    }
  }

  // Tries providers in @Order sequence; falls back to the next on any recoverable provider error
  // (unknown symbol, rate limit, upstream/server error), so a symbol missing or failing on the
  // primary provider (e.g. Binance) is still served by the secondary (e.g. CoinGecko).
  private List<PricePoint> fetchWithFallback(
      AssetType assetType, String symbol, ChartResolution chartResolution) {
    RuntimeException lastError = null;
    for (MarketPriceHistoryProvider provider : providers) {
      if (!provider.supports(assetType)) {
        continue;
      }
      try {
        return provider.fetchPriceHistory(assetType, symbol, chartResolution).stream()
            .sorted(Comparator.comparing(PricePoint::time))
            .toList();
      } catch (UnknownAssetSymbolException
          | MarketDataRateLimitException
          | MarketDataServerException e) {
        lastError = logAndCarry(provider, symbol, e);
      }
    }
    return failAfterFallback(assetType, lastError);
  }

  private List<PricePoint> fetchWithFallback(
      AssetType assetType, String symbol, HoldingsHistoryRange range) {
    RuntimeException lastError = null;
    for (MarketPriceHistoryProvider provider : providers) {
      if (!provider.supports(assetType)) {
        continue;
      }
      try {
        return provider.fetchPriceHistory(assetType, symbol, range).stream()
            .sorted(Comparator.comparing(PricePoint::time))
            .toList();
      } catch (UnknownAssetSymbolException
          | MarketDataRateLimitException
          | MarketDataServerException e) {
        lastError = logAndCarry(provider, symbol, e);
      }
    }
    return failAfterFallback(assetType, lastError);
  }

  private RuntimeException logAndCarry(
      MarketPriceHistoryProvider provider, String symbol, RuntimeException error) {
    log.debug(
        "Provider {} failed for {} ({}), trying next",
        provider.getClass().getSimpleName(),
        symbol,
        error.getClass().getSimpleName());
    return error;
  }

  private List<PricePoint> failAfterFallback(AssetType assetType, RuntimeException lastError) {
    if (lastError != null) {
      throw lastError;
    }
    throw new IllegalStateException("No market price history provider configured for " + assetType);
  }

  private List<PricePoint> getCachedByKey(AssetType assetType, String symbol, String key) {
    return priceHistoryCachePort.getPriceHistory(assetType, symbol, key).stream()
        .map(point -> new PricePoint(point.time(), point.price()))
        .sorted(Comparator.comparing(PricePoint::time))
        .toList();
  }

  private void storeIfNonEmpty(
      AssetType assetType, String symbol, String key, List<PricePoint> prices, Duration ttl) {
    if (prices.isEmpty()) {
      return;
    }
    priceHistoryCachePort.storePriceHistory(
        assetType,
        symbol,
        key,
        prices.stream()
            .map(p -> new PriceHistoryCachePort.PriceHistoryPoint(p.time(), p.price()))
            .toList(),
        ttl);
  }

  private List<PricePoint> waitForCachedLoad(AssetType assetType, String symbol, String key) {
    long deadline = System.nanoTime() + CACHE_LOAD_WAIT_TIMEOUT.toNanos();
    while (System.nanoTime() < deadline && !Thread.currentThread().isInterrupted()) {
      List<PricePoint> cached = getCachedByKey(assetType, symbol, key);
      if (!cached.isEmpty()) {
        return cached;
      }
      sleepBeforeRetry();
    }
    return getCachedByKey(assetType, symbol, key);
  }

  private void sleepBeforeRetry() {
    try {
      Thread.sleep(CACHE_LOAD_POLL_INTERVAL.toMillis());
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    }
  }

  private static String buildDynamicKey(ChartResolution r) {
    long startDay = alignToDay(r.start().getEpochSecond());
    long endDay = alignToDay(r.end().getEpochSecond());
    return "dyn:" + startDay + ":" + endDay + ":" + r.providerIntervalCode();
  }

  private static long alignToDay(long epochSeconds) {
    return epochSeconds - (epochSeconds % 86400L);
  }

  private static Duration deriveTtl(ChartResolution r) {
    if (r.interval().toDays() >= 1) return Duration.ofHours(6);
    if (r.interval().toHours() >= 4) return Duration.ofHours(2);
    return Duration.ofHours(1);
  }
}
