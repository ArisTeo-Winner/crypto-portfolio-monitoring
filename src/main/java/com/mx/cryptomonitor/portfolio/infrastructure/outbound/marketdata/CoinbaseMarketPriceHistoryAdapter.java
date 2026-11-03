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

/**
 * Proveedor PRIMARIO de velas de cripto (ADR-0021): Coinbase Advanced Trade (CDP) {@code GET
 * /api/v3/brokerage/products/{BASE-USD}/candles}, exchange regulado en EE. UU. y por tanto
 * accesible desde la IP US de Render (a diferencia de Binance/Bybit, que responden 451).
 *
 * <p>Autenticación por JWT ES256 efímero (ADR-0023): se firma un token por petición vía {@link
 * CoinbaseJwtSigner} y se envía en {@code Authorization: Bearer}. Si la credencial CDP no está
 * configurada, el adapter se salta (excepción recuperable → la cadena cae al siguiente proveedor).
 *
 * <p>La respuesta es {@code {"candles":[{start,low,high,open,close,volume}]}} con {@code start} en
 * segundos; se mapea el cierre. Coinbase topa 350 velas por petición, así que se pagina por
 * ventanas de {@code 350 * granularidad}. El intervalo del {@link ChartResolution} se traduce a la
 * granularidad enum de Advanced Trade.
 */
@Component
@Order(-20)
@Slf4j
public class CoinbaseMarketPriceHistoryAdapter implements MarketPriceHistoryProvider {

  private static final String QUOTE_SUFFIX = "-USD";
  private static final int MAX_CANDLES_PER_REQUEST = 350;

  private final WebClient webClient;
  private final CoinbaseJwtSigner jwtSigner;
  private final boolean enabled;
  private final Duration responseTimeout;
  private final ChartResolutionStrategy resolutionStrategy;
  private final Clock clock;

  @Autowired
  public CoinbaseMarketPriceHistoryAdapter(
      @Qualifier("coinbaseWebClient") WebClient webClient,
      CoinbaseJwtSigner jwtSigner,
      @Value("${external.providers.coinbase.enabled:true}") boolean enabled,
      @Value("${external.providers.coinbase.response-timeout:PT10S}") Duration responseTimeout,
      Clock clock) {
    this.webClient = webClient;
    this.jwtSigner = jwtSigner;
    this.enabled = enabled;
    this.responseTimeout = responseTimeout;
    this.resolutionStrategy = new ChartResolutionStrategy();
    this.clock = clock;
  }

  public CoinbaseMarketPriceHistoryAdapter(
      WebClient webClient, CoinbaseJwtSigner jwtSigner, Clock clock) {
    this(webClient, jwtSigner, true, Duration.ofSeconds(10), clock);
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
    if (!enabled || !jwtSigner.isConfigured()) {
      throw new UnknownAssetSymbolException("Coinbase adapter disabled or CDP credential missing");
    }
    String product = toProductId(symbol);
    Granularity granularity = toGranularity(chartResolution.interval());
    log.debug(
        "Coinbase fetch {} granularity={} from={} to={}",
        product,
        granularity.code(),
        chartResolution.start(),
        chartResolution.end());
    return fetchWithPagination(product, granularity, chartResolution);
  }

  private List<PricePoint> fetchWithPagination(
      String product, Granularity granularity, ChartResolution chartResolution) {
    List<PricePoint> allPoints = new ArrayList<>();
    Instant current = chartResolution.start();
    Instant end = chartResolution.end();
    Duration window = Duration.ofSeconds(granularity.seconds() * MAX_CANDLES_PER_REQUEST);

    while (current.isBefore(end)) {
      Instant windowEnd = min(current.plus(window), end);
      List<PricePoint> batch = fetchBatch(product, granularity, current, windowEnd);
      if (batch.isEmpty()) {
        current = windowEnd;
        continue;
      }
      allPoints.addAll(batch);
      Instant lastTime =
          batch.stream().map(PricePoint::time).max(Comparator.naturalOrder()).orElse(windowEnd);
      Instant next = lastTime.plusSeconds(granularity.seconds());
      current = next.isAfter(windowEnd) ? next : windowEnd;
    }

    return allPoints.stream().sorted(Comparator.comparing(PricePoint::time)).toList();
  }

  private List<PricePoint> fetchBatch(
      String product, Granularity granularity, Instant startTime, Instant endTime) {
    String path = "/api/v3/brokerage/products/" + product + "/candles";
    String bearer = jwtSigner.sign("GET", path);
    CandlesResponse response =
        webClient
            .get()
            .uri(
                uriBuilder ->
                    uriBuilder
                        .path(path)
                        .queryParam("start", startTime.getEpochSecond())
                        .queryParam("end", endTime.getEpochSecond())
                        .queryParam("granularity", granularity.code())
                        .build())
            .header("Authorization", "Bearer " + bearer)
            .retrieve()
            .onStatus(
                status -> status.value() == 400 || status.value() == 404,
                clientResponse ->
                    clientResponse
                        .bodyToMono(String.class)
                        .defaultIfEmpty("Invalid product or request")
                        .flatMap(
                            body ->
                                Mono.error(
                                    new UnknownAssetSymbolException(
                                        "Coinbase product not found: " + product))))
            .onStatus(
                status -> status.value() == 401 || status.value() == 403,
                clientResponse -> {
                  int status = clientResponse.statusCode().value();
                  log.warn(
                      "Coinbase CDP authentication failed (HTTP {}); check CDP key configuration",
                      status);
                  return clientResponse
                      .bodyToMono(String.class)
                      .defaultIfEmpty("unauthorized")
                      .flatMap(
                          body ->
                              Mono.error(
                                  new MarketDataServerException("Coinbase CDP auth failed")));
                })
            .onStatus(
                status -> status.value() == 429,
                clientResponse ->
                    clientResponse
                        .bodyToMono(String.class)
                        .defaultIfEmpty("Coinbase rate limit exceeded")
                        .map(MarketDataRateLimitException::new))
            .onStatus(
                HttpStatusCode::is5xxServerError,
                clientResponse ->
                    clientResponse
                        .bodyToMono(String.class)
                        .defaultIfEmpty("Coinbase server error")
                        .flatMap(
                            body ->
                                Mono.error(
                                    new MarketDataServerException("Coinbase error: " + body))))
            .bodyToMono(CandlesResponse.class)
            .timeout(responseTimeout)
            .onErrorMap(
                WebClientRequestException.class,
                ex -> new MarketDataServerException("Coinbase request failed: " + ex.getMessage()))
            .block();

    if (response == null || response.candles() == null || response.candles().isEmpty()) {
      return List.of();
    }

    return response.candles().stream().filter(this::isComplete).map(this::toPoint).toList();
  }

  private boolean isComplete(Candle candle) {
    return candle != null && candle.start() != null && candle.close() != null;
  }

  private PricePoint toPoint(Candle candle) {
    long startSeconds = Long.parseLong(candle.start().trim());
    BigDecimal closePrice = new BigDecimal(candle.close().trim());
    return new PricePoint(Instant.ofEpochSecond(startSeconds), closePrice);
  }

  private String toProductId(String symbol) {
    String normalized = symbol.trim().toUpperCase();
    return normalized.contains("-") ? normalized : normalized + QUOTE_SUFFIX;
  }

  // Advanced Trade acepta granularidades enumeradas; se redondea el intervalo del ChartResolution a
  // la soportada más cercana hacia arriba.
  private Granularity toGranularity(Duration interval) {
    long seconds = interval.getSeconds();
    if (seconds <= 60) return new Granularity("ONE_MINUTE", 60L);
    if (seconds <= 300) return new Granularity("FIVE_MINUTE", 300L);
    if (seconds <= 900) return new Granularity("FIFTEEN_MINUTE", 900L);
    if (seconds <= 1800) return new Granularity("THIRTY_MINUTE", 1800L);
    if (seconds <= 3600) return new Granularity("ONE_HOUR", 3600L);
    if (seconds <= 7200) return new Granularity("TWO_HOUR", 7200L);
    if (seconds <= 21600) return new Granularity("SIX_HOUR", 21600L);
    return new Granularity("ONE_DAY", 86400L);
  }

  private Instant min(Instant left, Instant right) {
    return left.isBefore(right) ? left : right;
  }

  private record Granularity(String code, long seconds) {}

  record CandlesResponse(List<Candle> candles) {}

  record Candle(String start, String low, String high, String open, String close, String volume) {}
}
