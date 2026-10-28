package com.mx.cryptomonitor.portfolio.infrastructure.outbound.marketdata;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import com.mx.cryptomonitor.portfolio.application.service.HoldingsHistoryRange;
import com.mx.cryptomonitor.portfolio.domain.exception.MarketDataRateLimitException;
import com.mx.cryptomonitor.portfolio.domain.exception.MarketDataServerException;
import com.mx.cryptomonitor.portfolio.domain.exception.UnknownAssetSymbolException;
import com.mx.cryptomonitor.portfolio.domain.model.AssetType;
import com.mx.cryptomonitor.portfolio.domain.model.ChartResolution;
import com.mx.cryptomonitor.portfolio.domain.model.ChartResolutionStrategy;
import com.mx.cryptomonitor.portfolio.domain.model.PricePoint;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

// Respaldo para CRYPTO (@Order 2): sirve monedas que Binance solo lista como perpetuo en
// USD-M Futures (p. ej. HYPEUSDT), donde el spot /api/v3/klines devuelve 400. Usa el precio
// indice (indexPriceKlines), compuesto a partir de spots reales, como proxy del precio spot.
// Va DESPUES de BybitMarketPriceHistoryAdapter (@Order 1), que da spot real y mas preciso;
// solo se invoca cuando spot de Binance y Bybit no resuelven el simbolo, via la cadena de
// fallback de CachedMarketPriceHistoryAdapter.
@Component
@Order(2)
@Slf4j
public class BinanceFuturesMarketPriceHistoryAdapter implements MarketPriceHistoryProvider {

  private static final String USDT_SUFFIX = "USDT";
  private static final int INDEX_KLINES_MAX_LIMIT = 1500;
  private static final ParameterizedTypeReference<List<List<Object>>> KLINES_TYPE =
      new ParameterizedTypeReference<>() {};

  private final WebClient webClient;
  private final boolean enabled;
  private final Duration responseTimeout;
  private final ChartResolutionStrategy resolutionStrategy;
  private final Clock clock;

  @Autowired
  public BinanceFuturesMarketPriceHistoryAdapter(
      @Qualifier("binanceFuturesWebClient") WebClient webClient,
      @Value("${external.providers.binance.futures.enabled:true}") boolean enabled,
      @Value("${external.providers.binance.futures.response-timeout:PT10S}")
          Duration responseTimeout,
      Clock clock) {
    this.webClient = webClient;
    this.enabled = enabled;
    this.responseTimeout = responseTimeout;
    this.resolutionStrategy = new ChartResolutionStrategy();
    this.clock = clock;
  }

  BinanceFuturesMarketPriceHistoryAdapter(WebClient webClient) {
    this(webClient, true, Duration.ofSeconds(10), Clock.systemUTC());
  }

  public BinanceFuturesMarketPriceHistoryAdapter(WebClient webClient, Clock clock) {
    this(webClient, true, Duration.ofSeconds(10), clock);
  }

  @Override
  public boolean supports(AssetType assetType) {
    return assetType == AssetType.CRYPTO;
  }

  @Override
  public List<PricePoint> fetchPriceHistory(
      AssetType assetType, String symbol, HoldingsHistoryRange range) {
    Instant end = Instant.now(clock);
    Instant start = range.isAll() ? end.minus(Duration.ofDays(365 * 5)) : range.startFrom(end);
    return fetchPriceHistory(assetType, symbol, resolutionStrategy.resolve(start, end));
  }

  @Override
  public List<PricePoint> fetchPriceHistory(
      AssetType assetType, String symbol, ChartResolution chartResolution) {
    if (!enabled) {
      throw new UnknownAssetSymbolException("Binance futures adapter is disabled");
    }
    String pair = symbol.trim().toUpperCase() + USDT_SUFFIX;
    log.debug(
        "Binance futures index fetch {} interval={} from={} to={}",
        pair,
        chartResolution.providerIntervalCode(),
        chartResolution.start(),
        chartResolution.end());
    return fetchWithPagination(pair, chartResolution);
  }

  private List<PricePoint> fetchWithPagination(String pair, ChartResolution chartResolution) {
    List<PricePoint> allPoints = new ArrayList<>();
    Instant current = chartResolution.start();
    Instant end = chartResolution.end();
    String intervalCode = chartResolution.providerIntervalCode();
    long intervalMs = chartResolution.interval().toMillis();

    while (current.isBefore(end)) {
      List<PricePoint> batch = fetchBatch(pair, intervalCode, current, end);
      if (batch.isEmpty()) {
        break;
      }
      allPoints.addAll(batch);
      current = batch.get(batch.size() - 1).time().plusMillis(intervalMs);
    }

    return allPoints.stream().sorted(Comparator.comparing(PricePoint::time)).toList();
  }

  private List<PricePoint> fetchBatch(
      String pair, String intervalCode, Instant startTime, Instant endTime) {
    List<List<Object>> klines =
        webClient
            .get()
            .uri(
                uriBuilder ->
                    uriBuilder
                        .path("/fapi/v1/indexPriceKlines")
                        .queryParam("pair", pair)
                        .queryParam("interval", intervalCode)
                        .queryParam("startTime", startTime.toEpochMilli())
                        .queryParam("endTime", endTime.toEpochMilli())
                        .queryParam("limit", INDEX_KLINES_MAX_LIMIT)
                        .build())
            .retrieve()
            .onStatus(
                status -> status.value() == 400,
                response ->
                    response
                        .bodyToMono(String.class)
                        .defaultIfEmpty("Invalid pair or request")
                        .flatMap(
                            body ->
                                Mono.error(
                                    new UnknownAssetSymbolException(
                                        "Binance futures pair not found: " + pair))))
            .onStatus(
                status -> status.value() == 429,
                response ->
                    response
                        .bodyToMono(String.class)
                        .defaultIfEmpty("Binance futures rate limit exceeded")
                        .map(MarketDataRateLimitException::new))
            .onStatus(
                HttpStatusCode::is5xxServerError,
                response ->
                    response
                        .bodyToMono(String.class)
                        .defaultIfEmpty("Binance futures server error")
                        .flatMap(
                            body ->
                                Mono.error(
                                    new MarketDataServerException(
                                        "Binance futures error: " + body))))
            .bodyToMono(KLINES_TYPE)
            .timeout(responseTimeout)
            .onErrorMap(
                WebClientRequestException.class,
                ex ->
                    new MarketDataServerException(
                        "Binance futures request failed: " + ex.getMessage()))
            .block();

    if (klines == null || klines.isEmpty()) {
      return List.of();
    }

    return klines.stream().filter(k -> k != null && k.size() >= 5).map(this::toPoint).toList();
  }

  private PricePoint toPoint(List<Object> kline) {
    long openTimeMs = Long.parseLong(String.valueOf(kline.get(0)));
    BigDecimal closePrice = new BigDecimal(String.valueOf(kline.get(4)));
    return new PricePoint(Instant.ofEpochMilli(openTimeMs), closePrice);
  }
}
