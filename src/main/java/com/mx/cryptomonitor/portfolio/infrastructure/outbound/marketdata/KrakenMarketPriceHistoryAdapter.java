package com.mx.cryptomonitor.portfolio.infrastructure.outbound.marketdata;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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
 * Proveedor SECUNDARIO de velas de cripto (ADR-0021/ADR-0022): Kraken {@code GET /0/public/OHLC},
 * exchange US-OK y sin API key. Se usa cuando Coinbase (primario) no cubre un símbolo, antes de
 * caer a CoinGecko.
 *
 * <p>Se pide {@code pair={CANÓNICO}USD&assetVersion=1}. Con {@code assetVersion=1} Kraken acepta el
 * símbolo canónico (p. ej. {@code BTCUSD}) y devuelve la llave del resultado ya en forma display
 * ({@code BTC/USD}); esa llave NO se expone (ACL, ADR-0022): se toma la única entrada de {@code
 * result} distinta de {@code last} y se leen sus velas. Cada vela es {@code [time, open, high, low,
 * close, vwap, volume, count]} con {@code time} en segundos; se mapea el cierre (índice 4). El
 * intervalo del {@link ChartResolution} se traduce a minutos (enum soportado por Kraken).
 *
 * <p>Kraken responde HTTP 200 incluso en error: el fallo viaja en el arreglo {@code error} del
 * cuerpo ({@code EQuery:Unknown asset pair} → símbolo desconocido; límites de tasa → rate limit),
 * así que se inspecciona antes del contrato. Todas las excepciones son recuperables para que la
 * cadena caiga al siguiente proveedor.
 */
@Component
@Order(-10)
@Slf4j
public class KrakenMarketPriceHistoryAdapter implements MarketPriceHistoryProvider {

  private static final String QUOTE_SUFFIX = "USD";
  private static final String LAST_KEY = "last";
  private static final int MAX_PAGES = 20;

  private final WebClient webClient;
  private final boolean enabled;
  private final Duration responseTimeout;
  private final ChartResolutionStrategy resolutionStrategy;
  private final Clock clock;

  @Autowired
  public KrakenMarketPriceHistoryAdapter(
      @Qualifier("krakenWebClient") WebClient webClient,
      @Value("${external.providers.kraken.enabled:true}") boolean enabled,
      @Value("${external.providers.kraken.response-timeout:PT8S}") Duration responseTimeout,
      Clock clock) {
    this.webClient = webClient;
    this.enabled = enabled;
    this.responseTimeout = responseTimeout;
    this.resolutionStrategy = new ChartResolutionStrategy();
    this.clock = clock;
  }

  public KrakenMarketPriceHistoryAdapter(WebClient webClient, Clock clock) {
    this(webClient, true, Duration.ofSeconds(8), clock);
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
      throw new UnknownAssetSymbolException("Kraken adapter disabled");
    }
    String pair = toPair(symbol);
    int intervalMinutes = toIntervalMinutes(chartResolution.interval());
    log.debug(
        "Kraken fetch {} interval={}min from={} to={}",
        pair,
        intervalMinutes,
        chartResolution.start(),
        chartResolution.end());
    return fetchWithPagination(pair, intervalMinutes, chartResolution);
  }

  private List<PricePoint> fetchWithPagination(
      String pair, int intervalMinutes, ChartResolution chartResolution) {
    long endSeconds = chartResolution.end().getEpochSecond();
    LinkedHashMap<Instant, BigDecimal> byTime = new LinkedHashMap<>();
    long since = chartResolution.start().getEpochSecond();

    for (int page = 0; page < MAX_PAGES; page++) {
      KrakenPage batch = fetchBatch(pair, intervalMinutes, since);
      long newest = since;
      for (PricePoint point : batch.points()) {
        if (point.time().getEpochSecond() <= endSeconds) {
          byTime.put(point.time(), point.price());
        }
        newest = Math.max(newest, point.time().getEpochSecond());
      }
      if (batch.points().isEmpty() || batch.last() <= since || newest >= endSeconds) {
        break;
      }
      since = batch.last();
    }

    return byTime.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .map(e -> new PricePoint(e.getKey(), e.getValue()))
        .toList();
  }

  private KrakenPage fetchBatch(String pair, int intervalMinutes, long since) {
    KrakenOhlcResponse response =
        webClient
            .get()
            .uri(
                uriBuilder ->
                    uriBuilder
                        .path("/0/public/OHLC")
                        .queryParam("pair", pair)
                        .queryParam("interval", intervalMinutes)
                        .queryParam("since", since)
                        .queryParam("assetVersion", 1)
                        .build())
            .retrieve()
            .onStatus(
                status -> status.value() == 429,
                clientResponse ->
                    clientResponse
                        .bodyToMono(String.class)
                        .defaultIfEmpty("Kraken rate limit exceeded")
                        .map(MarketDataRateLimitException::new))
            .onStatus(
                HttpStatusCode::is5xxServerError,
                clientResponse ->
                    clientResponse
                        .bodyToMono(String.class)
                        .defaultIfEmpty("Kraken server error")
                        .flatMap(
                            body ->
                                Mono.error(new MarketDataServerException("Kraken error: " + body))))
            .bodyToMono(KrakenOhlcResponse.class)
            .timeout(responseTimeout)
            .onErrorMap(
                WebClientRequestException.class,
                ex -> new MarketDataServerException("Kraken request failed: " + ex.getMessage()))
            .block();

    return parsePage(response, pair);
  }

  private KrakenPage parsePage(KrakenOhlcResponse response, String pair) {
    if (response == null) {
      return new KrakenPage(List.of(), 0L);
    }
    raiseIfBusinessError(response.error(), pair);
    if (response.result() == null) {
      return new KrakenPage(List.of(), 0L);
    }

    List<PricePoint> points = new ArrayList<>();
    for (Map.Entry<String, Object> entry : response.result().entrySet()) {
      if (LAST_KEY.equals(entry.getKey())) {
        continue;
      }
      if (entry.getValue() instanceof List<?> candles) {
        for (Object candle : candles) {
          PricePoint point = toPoint(candle);
          if (point != null) {
            points.add(point);
          }
        }
      }
    }
    return new KrakenPage(points, toLong(response.result().get(LAST_KEY)));
  }

  private void raiseIfBusinessError(List<String> errors, String pair) {
    if (errors == null || errors.isEmpty()) {
      return;
    }
    String joined = String.join(",", errors);
    String lower = joined.toLowerCase(Locale.ROOT);
    if (lower.contains("unknown asset pair") || lower.contains("unknown asset")) {
      throw new UnknownAssetSymbolException("Kraken unknown pair: " + pair);
    }
    if (lower.contains("rate limit") || lower.contains("too many requests")) {
      throw new MarketDataRateLimitException("Kraken rate limit: " + joined);
    }
    throw new MarketDataServerException("Kraken error: " + joined);
  }

  private PricePoint toPoint(Object candle) {
    if (!(candle instanceof List<?> fields) || fields.size() < 5) {
      return null;
    }
    Object timeField = fields.get(0);
    Object closeField = fields.get(4);
    if (timeField == null || closeField == null) {
      return null;
    }
    long timeSeconds = toLong(timeField);
    BigDecimal close = new BigDecimal(String.valueOf(closeField).trim());
    return new PricePoint(Instant.ofEpochSecond(timeSeconds), close);
  }

  private long toLong(Object value) {
    if (value instanceof Number number) {
      return number.longValue();
    }
    return value == null ? 0L : Long.parseLong(String.valueOf(value).trim());
  }

  private String toPair(String symbol) {
    String normalized = symbol.trim().toUpperCase(Locale.ROOT).replace("-", "").replace("/", "");
    return normalized.endsWith(QUOTE_SUFFIX) ? normalized : normalized + QUOTE_SUFFIX;
  }

  // Kraken acepta el intervalo en minutos de un enum fijo; se redondea el del ChartResolution a la
  // granularidad soportada más cercana hacia arriba.
  private int toIntervalMinutes(Duration interval) {
    long minutes = Math.max(1, interval.toMinutes());
    if (minutes <= 1) return 1;
    if (minutes <= 5) return 5;
    if (minutes <= 15) return 15;
    if (minutes <= 30) return 30;
    if (minutes <= 60) return 60;
    if (minutes <= 240) return 240;
    if (minutes <= 1440) return 1440;
    if (minutes <= 10080) return 10080;
    return 21600;
  }

  private record KrakenPage(List<PricePoint> points, long last) {}

  record KrakenOhlcResponse(List<String> error, Map<String, Object> result) {}
}
