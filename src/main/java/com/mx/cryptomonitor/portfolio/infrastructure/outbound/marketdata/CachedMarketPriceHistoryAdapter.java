package com.mx.cryptomonitor.portfolio.infrastructure.outbound.marketdata;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Component;

import com.mx.cryptomonitor.portfolio.application.port.out.MarketPriceHistoryPort;
import com.mx.cryptomonitor.portfolio.application.port.out.PriceHistoryCachePort;
import com.mx.cryptomonitor.portfolio.application.service.HoldingsHistoryRange;
import com.mx.cryptomonitor.portfolio.domain.model.AssetType;
import com.mx.cryptomonitor.portfolio.domain.model.PricePoint;

@Component
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
    List<PricePoint> cached = getCached(assetType, symbol, parsedRange.value());
    if (!cached.isEmpty()) {
      return cached;
    }

    if (!priceHistoryCachePort.acquireLoadLock(
        assetType, symbol, parsedRange.value(), LOAD_LOCK_TTL)) {
      return waitForCachedLoad(assetType, symbol, parsedRange.value());
    }

    try {
      MarketPriceHistoryProvider provider =
          providers.stream()
              .filter(candidate -> candidate.supports(assetType))
              .findFirst()
              .orElseThrow(
                  () ->
                      new IllegalStateException(
                          "No market price history provider configured for " + assetType));
      List<PricePoint> prices =
          provider.fetchPriceHistory(assetType, symbol, parsedRange).stream()
              .sorted(Comparator.comparing(PricePoint::time))
              .toList();
      if (!prices.isEmpty()) {
        priceHistoryCachePort.storePriceHistory(
            assetType,
            symbol,
            parsedRange.value(),
            prices.stream()
                .map(point -> new PriceHistoryCachePort.PriceHistoryPoint(point.time(), point.price()))
                .toList(),
            parsedRange.ttl());
      }
      return prices;
    } finally {
      priceHistoryCachePort.releaseLoadLock(assetType, symbol, parsedRange.value());
    }
  }

  private List<PricePoint> getCached(AssetType assetType, String symbol, String range) {
    return priceHistoryCachePort.getPriceHistory(assetType, symbol, range).stream()
        .map(point -> new PricePoint(point.time(), point.price()))
        .sorted(Comparator.comparing(PricePoint::time))
        .toList();
  }

  private List<PricePoint> waitForCachedLoad(AssetType assetType, String symbol, String range) {
    long deadline = System.nanoTime() + CACHE_LOAD_WAIT_TIMEOUT.toNanos();
    while (System.nanoTime() < deadline && !Thread.currentThread().isInterrupted()) {
      List<PricePoint> cached = getCached(assetType, symbol, range);
      if (!cached.isEmpty()) {
        return cached;
      }
      sleepBeforeRetry();
    }
    return getCached(assetType, symbol, range);
  }

  private void sleepBeforeRetry() {
    try {
      Thread.sleep(CACHE_LOAD_POLL_INTERVAL.toMillis());
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    }
  }
}
