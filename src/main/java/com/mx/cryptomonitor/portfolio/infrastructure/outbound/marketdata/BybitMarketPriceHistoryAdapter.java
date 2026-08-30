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
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mx.cryptomonitor.portfolio.application.service.HoldingsHistoryRange;
import com.mx.cryptomonitor.portfolio.domain.exception.MarketDataRateLimitException;
import com.mx.cryptomonitor.portfolio.domain.exception.MarketDataServerException;
import com.mx.cryptomonitor.portfolio.domain.exception.UnknownAssetSymbolException;
import com.mx.cryptomonitor.portfolio.domain.model.AssetType;
import com.mx.cryptomonitor.portfolio.domain.model.ChartResolution;
import com.mx.cryptomonitor.portfolio.domain.model.ChartResolutionStrategy;
import com.mx.cryptomonitor.portfolio.domain.model.PricePoint;

import lombok.extern.slf4j.Slf4j;

// Fuente de SPOT real para CRYPTO (@Order 1): sirve monedas que Binance no lista en spot (p. ej.
// HYPEUSDT, que da 400 en /api/v3/klines) usando el precio spot de Bybit, mas preciso que el
// indice de futuros de Binance para valuar el holding. Solo se invoca cuando el adapter de spot
// de Binance lanza UnknownAssetSymbolException, via la cadena de fallback de
// CachedMarketPriceHistoryAdapter. Bybit v5 difiere de Binance: senala error con retCode en el
// body (HTTP 200), devuelve la lista de nuevo->antiguo, y usa su propio vocabulario de intervalos.
@Component
@Order(1)
@Slf4j
public class BybitMarketPriceHistoryAdapter implements MarketPriceHistoryProvider {

  private static final String USDT_SUFFIX = "USDT";
  private static final int KLINE_MAX_LIMIT = 1000;
  private static final int RET_CODE_OK = 0;
  private static final int RET_CODE_UNKNOWN_SYMBOL = 10001;

  private final WebClient webClient;
  private final boolean enabled;
  private final Duration responseTimeout;
  private final ChartResolutionStrategy resolutionStrategy;
  private final Clock clock;

  @Autowired
  public BybitMarketPriceHistoryAdapter(
      @Qualifier("bybitWebClient") WebClient webClient,
      @Value("${external.providers.bybit.enabled:true}") boolean enabled,
      @Value("${external.providers.bybit.response-timeout:PT10S}") Duration responseTimeout,
      Clock clock) {
    this.webClient = webClient;
    this.enabled = enabled;
    this.responseTimeout = responseTimeout;
    this.resolutionStrategy = new ChartResolutionStrategy();
    this.clock = clock;
  }

  BybitMarketPriceHistoryAdapter(WebClient webClient) {
    this(webClient, true, Duration.ofSeconds(10), Clock.systemUTC());
  }

  public BybitMarketPriceHistoryAdapter(WebClient webClient, Clock clock) {
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
      throw new UnknownAssetSymbolException("Bybit adapter is disabled");
    }
    String bybitSymbol = symbol.trim().toUpperCase() + USDT_SUFFIX;
    String interval = mapInterval(chartResolution.providerIntervalCode());
    log.debug(
        "Bybit spot fetch {} interval={} from={} to={}",
        bybitSymbol,
        interval,
        chartResolution.start(),
        chartResolution.end());
    return fetchWithPagination(bybitSymbol, interval, chartResolution);
  }

  // Bybit no tiene intervalo de 8h; se aproxima a 4h (mas fino), correcto pero mas denso.
  private static String mapInterval(String binanceCode) {
    return switch (binanceCode) {
      case "1m" -> "1";
      case "5m" -> "5";
      case "15m" -> "15";
      case "1h" -> "60";
      case "4h", "8h" -> "240";
      default -> "D";
    };
  }

  // Bybit devuelve la ventana [start, end] de nuevo->antiguo, hasta `limit` velas mas cercanas a
  // `end`. Para cubrir rangos largos se pagina hacia atras: se colecta la ventana y se mueve `end`
  // justo antes de la vela mas antigua recibida.
  private List<PricePoint> fetchWithPagination(
      String bybitSymbol, String interval, ChartResolution chartResolution) {
    List<PricePoint> allPoints = new ArrayList<>();
    Instant start = chartResolution.start();
    Instant windowEnd = chartResolution.end();

    while (windowEnd.isAfter(start)) {
      List<PricePoint> batch = fetchBatch(bybitSymbol, interval, start, windowEnd);
      if (batch.isEmpty()) {
        break;
      }
      allPoints.addAll(batch);
      Instant oldest =
          batch.stream().map(PricePoint::time).min(Comparator.naturalOrder()).orElseThrow();
      if (!oldest.isAfter(start) || batch.size() < KLINE_MAX_LIMIT) {
        break;
      }
      windowEnd = oldest.minusMillis(1);
    }

    return allPoints.stream().sorted(Comparator.comparing(PricePoint::time)).toList();
  }

  private List<PricePoint> fetchBatch(
      String bybitSymbol, String interval, Instant startTime, Instant endTime) {
    BybitKlineResponse response =
        webClient
            .get()
            .uri(
                uriBuilder ->
                    uriBuilder
                        .path("/v5/market/kline")
                        .queryParam("category", "spot")
                        .queryParam("symbol", bybitSymbol)
                        .queryParam("interval", interval)
                        .queryParam("start", startTime.toEpochMilli())
                        .queryParam("end", endTime.toEpochMilli())
                        .queryParam("limit", KLINE_MAX_LIMIT)
                        .build())
            .retrieve()
            .onStatus(
                status -> status.value() == 429,
                resp ->
                    resp.bodyToMono(String.class)
                        .defaultIfEmpty("Bybit rate limit exceeded")
                        .map(MarketDataRateLimitException::new))
            .onStatus(
                HttpStatusCode::is5xxServerError,
                resp ->
                    resp.bodyToMono(String.class)
                        .defaultIfEmpty("Bybit server error")
                        .map(body -> new MarketDataServerException("Bybit error: " + body)))
            .bodyToMono(BybitKlineResponse.class)
            .timeout(responseTimeout)
            .onErrorMap(
                WebClientRequestException.class,
                ex -> new MarketDataServerException("Bybit request failed: " + ex.getMessage()))
            .block();

    if (response == null) {
      return List.of();
    }
    int retCode = response.retCode() == null ? RET_CODE_OK : response.retCode();
    if (retCode != RET_CODE_OK) {
      throw mapBodyError(retCode, response.retMsg(), bybitSymbol);
    }
    if (response.result() == null || response.result().list() == null) {
      return List.of();
    }

    return response.result().list().stream()
        .filter(row -> row != null && row.size() >= 5)
        .map(this::toPoint)
        .toList();
  }

  private RuntimeException mapBodyError(int retCode, String retMsg, String bybitSymbol) {
    if (retCode == RET_CODE_UNKNOWN_SYMBOL) {
      return new UnknownAssetSymbolException("Bybit symbol not found: " + bybitSymbol);
    }
    return new MarketDataServerException("Bybit error retCode=" + retCode + " " + retMsg);
  }

  private PricePoint toPoint(List<String> row) {
    long openTimeMs = Long.parseLong(row.get(0));
    BigDecimal closePrice = new BigDecimal(row.get(4));
    return new PricePoint(Instant.ofEpochMilli(openTimeMs), closePrice);
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record BybitKlineResponse(Integer retCode, String retMsg, BybitResult result) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record BybitResult(List<List<String>> list) {}
}
