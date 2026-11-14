package com.mx.cryptomonitor.portfolio.infrastructure.outbound.marketdata;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.mx.cryptomonitor.portfolio.application.port.out.MarketPriceHistoryPort;
import com.mx.cryptomonitor.portfolio.application.port.out.PriceHistoryCachePort;
import com.mx.cryptomonitor.portfolio.application.service.HoldingsHistoryRange;
import com.mx.cryptomonitor.portfolio.domain.exception.MarketDataRateLimitException;
import com.mx.cryptomonitor.portfolio.domain.exception.MarketDataServerException;
import com.mx.cryptomonitor.portfolio.domain.exception.NoMarketDataProviderException;
import com.mx.cryptomonitor.portfolio.domain.exception.UnknownAssetSymbolException;
import com.mx.cryptomonitor.portfolio.domain.model.AssetType;
import com.mx.cryptomonitor.portfolio.domain.model.ChartResolution;
import com.mx.cryptomonitor.portfolio.domain.model.PricePoint;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class CachedMarketPriceHistoryAdapter implements MarketPriceHistoryPort {

  private static final Duration LOAD_LOCK_TTL = Duration.ofSeconds(30);
  private static final Duration CACHE_LOAD_WAIT_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration CACHE_LOAD_POLL_INTERVAL = Duration.ofMillis(50);

  private final PriceHistoryCachePort priceHistoryCachePort;
  private final List<MarketPriceHistoryProvider> providers;
  private final MeterRegistry meterRegistry;

  @Autowired
  public CachedMarketPriceHistoryAdapter(
      PriceHistoryCachePort priceHistoryCachePort,
      List<MarketPriceHistoryProvider> providers,
      MeterRegistry meterRegistry) {
    this.priceHistoryCachePort = priceHistoryCachePort;
    this.providers = providers;
    this.meterRegistry = meterRegistry;
  }

  public CachedMarketPriceHistoryAdapter(
      PriceHistoryCachePort priceHistoryCachePort, List<MarketPriceHistoryProvider> providers) {
    this(priceHistoryCachePort, providers, new SimpleMeterRegistry());
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
  // primary provider (e.g. Coinbase) is still served by the secondary (e.g. Kraken/CoinGecko).
  private List<PricePoint> fetchWithFallback(
      AssetType assetType, String symbol, ChartResolution chartResolution) {
    return fetchWithFallback(
        assetType,
        symbol,
        provider -> provider.fetchPriceHistory(assetType, symbol, chartResolution));
  }

  private List<PricePoint> fetchWithFallback(
      AssetType assetType, String symbol, HoldingsHistoryRange range) {
    return fetchWithFallback(
        assetType, symbol, provider -> provider.fetchPriceHistory(assetType, symbol, range));
  }

  // Observabilidad (ADR-0021/ADR-0022 F3): cada intento emite el counter
  // marketdata.pricehistory.fetch{provider,asset_type,outcome} (tags de baja cardinalidad, sin
  // symbol) y el proveedor ganador se registra con el símbolo en un log INFO. El outcome
  // server_error de un provider Coinbase cubre la alerta de credencial 401/403 (ADR-0023).
  private List<PricePoint> fetchWithFallback(
      AssetType assetType, String symbol, ProviderFetch fetch) {
    RuntimeException lastError = null;
    for (MarketPriceHistoryProvider provider : providers) {
      if (!provider.supports(assetType)) {
        continue;
      }
      try {
        List<PricePoint> prices =
            fetch.apply(provider).stream().sorted(Comparator.comparing(PricePoint::time)).toList();
        recordOutcome(provider, assetType, "success");
        log.info(
            "price history for {} [{}] served by {} ({} points)",
            symbol,
            assetType,
            provider.getClass().getSimpleName(),
            prices.size());
        return prices;
      } catch (UnknownAssetSymbolException e) {
        lastError = carry(provider, symbol, assetType, e, "unknown_symbol");
      } catch (MarketDataRateLimitException e) {
        lastError = carry(provider, symbol, assetType, e, "rate_limit");
      } catch (MarketDataServerException e) {
        lastError = carry(provider, symbol, assetType, e, "server_error");
      }
    }
    return failAfterFallback(assetType, lastError);
  }

  private RuntimeException carry(
      MarketPriceHistoryProvider provider,
      String symbol,
      AssetType assetType,
      RuntimeException error,
      String outcome) {
    recordOutcome(provider, assetType, outcome);
    log.debug(
        "Provider {} failed for {} [{}] ({}:{}), trying next",
        provider.getClass().getSimpleName(),
        symbol,
        assetType,
        outcome,
        error.getClass().getSimpleName());
    return error;
  }

  private void recordOutcome(
      MarketPriceHistoryProvider provider, AssetType assetType, String outcome) {
    meterRegistry
        .counter(
            "marketdata.pricehistory.fetch",
            "provider",
            provider.getClass().getSimpleName(),
            "asset_type",
            assetType.name(),
            "outcome",
            outcome)
        .increment();
  }

  @FunctionalInterface
  private interface ProviderFetch {
    List<PricePoint> apply(MarketPriceHistoryProvider provider);
  }

  private List<PricePoint> failAfterFallback(AssetType assetType, RuntimeException lastError) {
    if (lastError != null) {
      throw lastError;
    }
    // Ningun proveedor soporta este AssetType (p.ej. GOVERNMENT_BOND). Excepcion recuperable por
    // activo (ADR-0018): el caller la degrada a "no disponible" (respuesta parcial) en vez de 500.
    throw new NoMarketDataProviderException(
        "No market price history provider configured for " + assetType);
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
