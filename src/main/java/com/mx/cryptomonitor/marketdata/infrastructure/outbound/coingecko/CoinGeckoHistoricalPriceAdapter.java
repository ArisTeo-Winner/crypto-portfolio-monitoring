package com.mx.cryptomonitor.marketdata.infrastructure.outbound.coingecko;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import com.mx.cryptomonitor.marketdata.application.port.out.CryptoHistoricalPricePoint;
import com.mx.cryptomonitor.marketdata.application.port.out.CryptoHistoricalPricePort;
import com.mx.cryptomonitor.marketdata.application.port.out.CryptoHistoricalPriceSeries;
import com.mx.cryptomonitor.marketdata.domain.exception.CoinGeckoInvalidAssetException;
import com.mx.cryptomonitor.marketdata.domain.exception.CoinGeckoRateLimitException;
import com.mx.cryptomonitor.marketdata.domain.exception.CoinGeckoServerException;
import com.mx.cryptomonitor.marketdata.infrastructure.configuration.CoinGeckoProperties;
import com.mx.cryptomonitor.marketdata.infrastructure.outbound.coingecko.dto.CoinGeckoMarketChartRangeResponse;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class CoinGeckoHistoricalPriceAdapter implements CryptoHistoricalPricePort {

  public static final String CACHE_NAME = "cryptoHistoricalPrices";
  private static final Duration DEFAULT_RESPONSE_TIMEOUT = Duration.ofSeconds(8);
  private static final String USD = "usd";

  private final WebClient webClient;
  private final CoinGeckoProperties properties;
  private final CacheManager cacheManager;
  private final MeterRegistry meterRegistry;

  @Autowired
  public CoinGeckoHistoricalPriceAdapter(
      @Qualifier("coinGeckoWebClient") WebClient webClient,
      CoinGeckoProperties properties,
      CacheManager cacheManager,
      MeterRegistry meterRegistry) {
    this.webClient = webClient;
    this.properties = properties;
    this.cacheManager = cacheManager;
    this.meterRegistry = meterRegistry;
  }

  public CoinGeckoHistoricalPriceAdapter(
      WebClient webClient, CoinGeckoProperties properties, CacheManager cacheManager) {
    this(webClient, properties, cacheManager, new SimpleMeterRegistry());
  }

  @Override
  public Mono<CryptoHistoricalPriceSeries> getHistoricalUsdPrices(
      String assetId, Instant fromInclusive, Instant toInclusive) {
    String normalizedAssetId = normalizeAssetId(assetId);
    Instant normalizedFrom = normalizeInstant(fromInclusive, "fromInclusive");
    Instant normalizedTo = normalizeInstant(toInclusive, "toInclusive");

    if (normalizedFrom.isAfter(normalizedTo)) {
      return Mono.error(
          new CoinGeckoInvalidAssetException(
              "fromInclusive must be before or equal to toInclusive"));
    }

    if (!properties.enabled()) {
      return Mono.error(
          new CoinGeckoServerException("CoinGecko historical integration is disabled"));
    }

    String cacheKey = buildRangeCacheKey(normalizedAssetId, normalizedFrom, normalizedTo);
    return fetchSeries(
        "range",
        normalizedAssetId,
        cacheKey,
        uriBuilder ->
            uriBuilder
                .path("/coins/{id}/market_chart/range")
                .queryParam("vs_currency", USD)
                .queryParam("from", normalizedFrom.getEpochSecond())
                .queryParam("to", normalizedTo.getEpochSecond())
                .build(normalizedAssetId));
  }

  @Override
  public Mono<CryptoHistoricalPriceSeries> getHistoricalUsdPrices(String assetId, int days) {
    String normalizedAssetId = normalizeAssetId(assetId);
    int normalizedDays = normalizeDays(days);

    if (!properties.enabled()) {
      return Mono.error(
          new CoinGeckoServerException("CoinGecko historical integration is disabled"));
    }

    String cacheKey = buildDaysCacheKey(normalizedAssetId, normalizedDays);
    return fetchSeries(
        "days",
        normalizedAssetId,
        cacheKey,
        uriBuilder ->
            uriBuilder
                .path("/coins/{id}/market_chart")
                .queryParam("vs_currency", USD)
                .queryParam("days", normalizedDays)
                .build(normalizedAssetId));
  }

  private CryptoHistoricalPriceSeries mapSeries(
      String assetId, CoinGeckoMarketChartRangeResponse response) {
    if (response == null || response.prices() == null || response.prices().isEmpty()) {
      throw new CoinGeckoServerException("CoinGecko returned no historical prices for " + assetId);
    }

    List<CryptoHistoricalPricePoint> points =
        response.prices().stream()
            .map(this::mapPoint)
            .sorted(Comparator.comparing(CryptoHistoricalPricePoint::timestamp))
            .toList();

    if (points.isEmpty()) {
      throw new CoinGeckoServerException("CoinGecko returned no valid price points for " + assetId);
    }

    return new CryptoHistoricalPriceSeries(assetId, USD, points);
  }

  private CryptoHistoricalPricePoint mapPoint(List<java.math.BigDecimal> rawPoint) {
    if (rawPoint == null || rawPoint.size() < 2) {
      throw new CoinGeckoServerException("Malformed CoinGecko historical price point");
    }

    Instant timestamp = Instant.ofEpochMilli(rawPoint.get(0).longValue());
    return new CryptoHistoricalPricePoint(timestamp, rawPoint.get(1));
  }

  private Duration resolveTimeout() {
    return properties.responseTimeout() != null
        ? properties.responseTimeout()
        : DEFAULT_RESPONSE_TIMEOUT;
  }

  private Mono<CryptoHistoricalPriceSeries> fetchSeries(
      String mode,
      String assetId,
      String cacheKey,
      Function<org.springframework.web.util.UriBuilder, java.net.URI> uriBuilderFunction) {
    CryptoHistoricalPriceSeries cachedSeries = getCachedSeries(cacheKey);
    if (cachedSeries != null) {
      log.debug("CoinGecko historical cache hit for {}", cacheKey);
      recordRequest(mode, "cache", "success", null);
      return Mono.just(cachedSeries);
    }

    Timer.Sample sample = Timer.start(meterRegistry);
    return webClient
        .get()
        .uri(uriBuilderFunction)
        .retrieve()
        .onStatus(
            status -> status.value() == 429,
            response ->
                response
                    .bodyToMono(String.class)
                    .defaultIfEmpty("CoinGecko rate limit exceeded")
                    .flatMap(body -> Mono.error(new CoinGeckoRateLimitException(body))))
        .onStatus(
            status -> status.value() == 404,
            response ->
                response
                    .bodyToMono(String.class)
                    .defaultIfEmpty("CoinGecko asset not found")
                    .flatMap(
                        body ->
                            Mono.error(
                                new CoinGeckoInvalidAssetException(
                                    "CoinGecko asset not found: " + assetId))))
        .onStatus(
            HttpStatusCode::is4xxClientError,
            response ->
                response
                    .bodyToMono(String.class)
                    .defaultIfEmpty("CoinGecko client error")
                    .flatMap(
                        body ->
                            Mono.error(
                                new CoinGeckoServerException(
                                    "CoinGecko client error "
                                        + response.statusCode()
                                        + ": "
                                        + body))))
        .onStatus(
            HttpStatusCode::is5xxServerError,
            response ->
                response
                    .bodyToMono(String.class)
                    .defaultIfEmpty("CoinGecko server error")
                    .flatMap(
                        body ->
                            Mono.error(
                                new CoinGeckoServerException(
                                    "CoinGecko server error "
                                        + response.statusCode()
                                        + ": "
                                        + body))))
        .bodyToMono(CoinGeckoMarketChartRangeResponse.class)
        .timeout(resolveTimeout())
        .onErrorMap(
            WebClientRequestException.class,
            ex -> new CoinGeckoServerException("CoinGecko request failed", ex))
        .map(response -> mapSeries(assetId, response))
        .doOnNext(series -> putCachedSeries(cacheKey, series))
        .doOnSuccess(
            series -> {
              sample.stop(
                  meterRegistry.timer(
                      "cryptomonitor.marketdata.coingecko.historical.latency",
                      "mode",
                      mode,
                      "outcome",
                      "success"));
              recordRequest(mode, "remote", "success", null);
              log.debug(
                  "CoinGecko historical prices loaded for {} with {} points",
                  assetId,
                  series != null ? series.points().size() : 0);
            })
        .doOnError(
            ex -> {
              sample.stop(
                  meterRegistry.timer(
                      "cryptomonitor.marketdata.coingecko.historical.latency",
                      "mode",
                      mode,
                      "outcome",
                      "failure"));
              recordRequest(mode, "remote", "failure", ex.getClass().getSimpleName());
            });
  }

  private String normalizeAssetId(String assetId) {
    if (assetId == null || assetId.isBlank()) {
      throw new CoinGeckoInvalidAssetException("assetId cannot be empty");
    }
    return assetId.trim().toLowerCase();
  }

  private Instant normalizeInstant(Instant value, String field) {
    if (value == null) {
      throw new CoinGeckoInvalidAssetException(field + " cannot be null");
    }
    return value;
  }

  private int normalizeDays(int days) {
    if (days <= 0) {
      throw new CoinGeckoInvalidAssetException("days must be greater than zero");
    }
    return days;
  }

  private String buildRangeCacheKey(String assetId, Instant fromInclusive, Instant toInclusive) {
    return assetId + ":" + fromInclusive.getEpochSecond() + ":" + toInclusive.getEpochSecond();
  }

  private String buildDaysCacheKey(String assetId, int days) {
    return assetId + ":days:" + days;
  }

  private CryptoHistoricalPriceSeries getCachedSeries(String cacheKey) {
    try {
      Cache cache = cacheManager.getCache(CACHE_NAME);
      if (cache == null) {
        return null;
      }
      return cache.get(cacheKey, CryptoHistoricalPriceSeries.class);
    } catch (RuntimeException ex) {
      log.warn("CoinGecko historical cache read failed for {}", cacheKey, ex);
      return null;
    }
  }

  private void putCachedSeries(String cacheKey, CryptoHistoricalPriceSeries series) {
    try {
      Cache cache = cacheManager.getCache(CACHE_NAME);
      if (cache != null) {
        cache.put(cacheKey, series);
      }
    } catch (RuntimeException ex) {
      log.warn("CoinGecko historical cache write failed for {}", cacheKey, ex);
    }
  }

  private void recordRequest(String mode, String source, String outcome, String error) {
    if (error == null) {
      meterRegistry
          .counter(
              "cryptomonitor.marketdata.coingecko.historical.requests",
              "mode",
              mode,
              "source",
              source,
              "outcome",
              outcome)
          .increment();
      return;
    }
    meterRegistry
        .counter(
            "cryptomonitor.marketdata.coingecko.historical.requests",
            "mode",
            mode,
            "source",
            source,
            "outcome",
            outcome,
            "error",
            error)
        .increment();
  }
}
