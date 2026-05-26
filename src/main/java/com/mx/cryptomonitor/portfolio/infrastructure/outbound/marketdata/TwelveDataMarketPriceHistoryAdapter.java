package com.mx.cryptomonitor.portfolio.infrastructure.outbound.marketdata;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import com.mx.cryptomonitor.portfolio.application.service.HoldingsHistoryRange;
import com.mx.cryptomonitor.portfolio.domain.exception.MarketDataRateLimitException;
import com.mx.cryptomonitor.portfolio.domain.exception.MarketDataServerException;
import com.mx.cryptomonitor.portfolio.domain.exception.UnknownAssetSymbolException;
import com.mx.cryptomonitor.portfolio.domain.model.AssetType;
import com.mx.cryptomonitor.portfolio.domain.model.ChartResolution;
import com.mx.cryptomonitor.portfolio.domain.model.PricePoint;
import com.mx.cryptomonitor.portfolio.domain.model.Resolution;

import lombok.extern.slf4j.Slf4j;

/**
 * Adaptador Twelve Data para el módulo portfolio.
 *
 * <p>Proveedor primario (@Order 5) de series de precios para activos STOCK / ETF / INDEX. Soporta
 * resolución diaria (interval=1day) e intraday (interval=1h) según el {@link HoldingsHistoryRange}.
 *
 * <p>Manejo de errores:
 *
 * <ul>
 *   <li>HTTP 401 / 403 o body con code 401/403 → {@link MarketDataServerException} con mensaje
 *       claro sobre credenciales. Se loguea como ERROR. El {@code CachedMarketPriceHistoryAdapter}
 *       hace fallback al siguiente proveedor configurado.
 *   <li>HTTP 429 o body con code 429 → {@link MarketDataRateLimitException}. Fallback.
 *   <li>HTTP 404 o body con code 400/404 → {@link UnknownAssetSymbolException}. Sin fallback.
 *   <li>HTTP 5xx o body con otro code → {@link MarketDataServerException}. Fallback.
 *   <li>Error de red / timeout → {@link MarketDataServerException}. Fallback.
 * </ul>
 *
 * <p>Mapeo de intervalos desde {@link HoldingsHistoryRange}:
 *
 * <pre>
 *   stepSeconds ≤ 3600  (24h range)  → 1h
 *   stepSeconds = 86400 (7d…all)     → 1day
 *   ChartResolution.providerIntervalCode presente → se usa directamente
 * </pre>
 *
 * <p>Plan gratuito de Twelve Data: 800 req/día, 8 req/min.
 */
@Component
@ConditionalOnExpression(
    "'${marketdata.twelvedata.base-url:}' != '' and '${marketdata.twelvedata.api-key:}' != ''")
@Order(5)
@Slf4j
public class TwelveDataMarketPriceHistoryAdapter implements MarketPriceHistoryProvider {

  /** Formato de datetime intraday que devuelve Twelve Data: "2026-05-22 15:30:00". */
  private static final DateTimeFormatter INTRADAY_FMT =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  /**
   * Timezone de fallback para parsear datetimes intraday cuando el proveedor no incluye
   * exchange_timezone en el meta.
   */
  private static final ZoneId DEFAULT_EXCHANGE_ZONE = ZoneId.of("America/New_York");

  /** Máximo outputsize admitido por la API. */
  private static final int MAX_OUTPUT_SIZE = 5000;

  private final WebClient webClient;
  private final String baseUrl;
  private final String apiKey;

  public TwelveDataMarketPriceHistoryAdapter(
      WebClient.Builder webClientBuilder,
      @Value("${marketdata.twelvedata.base-url:https://api.twelvedata.com}") String baseUrl,
      @Value("${marketdata.twelvedata.api-key}") String apiKey) {
    this.webClient = webClientBuilder.build();
    this.baseUrl = baseUrl.replaceAll("/$", "");
    this.apiKey = apiKey;
  }

  // -------------------------------------------------------------------------
  // MarketPriceHistoryProvider — supports
  // -------------------------------------------------------------------------

  @Override
  public boolean supports(AssetType assetType) {
    return assetType == AssetType.STOCK
        || assetType == AssetType.ETF
        || assetType == AssetType.INDEX;
  }

  // -------------------------------------------------------------------------
  // fetchPriceHistory — HoldingsHistoryRange
  // -------------------------------------------------------------------------

  @Override
  public List<PricePoint> fetchPriceHistory(
      AssetType assetType, String symbol, HoldingsHistoryRange range) {
    String interval = resolveInterval(range);
    int outputSize = resolveOutputSize(range, interval);

    LocalDate to = LocalDate.now(ZoneOffset.UTC);
    LocalDate from = range.isAll() ? LocalDate.of(2000, 1, 1) : to.minusDays(range.days());

    log.debug(
        "TwelveData time_series: symbol={} interval={} outputsize={} from={} to={}",
        symbol,
        interval,
        outputSize,
        from,
        to);

    Map<String, Object> json = callTimeSeries(symbol, interval, outputSize, from, to);
    return extractSeries(json, range.resolution());
  }

  // -------------------------------------------------------------------------
  // fetchPriceHistory — ChartResolution (override del default de la interfaz)
  // -------------------------------------------------------------------------

  @Override
  public List<PricePoint> fetchPriceHistory(
      AssetType assetType, String symbol, ChartResolution chartResolution) {
    String interval =
        (chartResolution.providerIntervalCode() != null
                && !chartResolution.providerIntervalCode().isBlank())
            ? chartResolution.providerIntervalCode()
            : resolveIntervalFromDuration(chartResolution.interval());

    long days = Duration.between(chartResolution.start(), chartResolution.end()).toDays();
    int outputSize = Math.min((int) Math.max(days * pointsPerDay(interval), 1), MAX_OUTPUT_SIZE);

    LocalDate from = chartResolution.start().atZone(ZoneOffset.UTC).toLocalDate();
    LocalDate to = chartResolution.end().atZone(ZoneOffset.UTC).toLocalDate();

    log.debug(
        "TwelveData time_series (ChartResolution): symbol={} interval={} outputsize={} from={} to={}",
        symbol,
        interval,
        outputSize,
        from,
        to);

    Map<String, Object> json = callTimeSeries(symbol, interval, outputSize, from, to);
    return extractSeries(json, chartResolution.toAggregationResolution());
  }

  // -------------------------------------------------------------------------
  // HTTP — llamada al endpoint /time_series
  // -------------------------------------------------------------------------

  /**
   * Llama a {@code /time_series} de Twelve Data <em>sin</em> los parámetros {@code start_date} /
   * {@code end_date}.
   *
   * <p>El plan gratuito rechaza esos filtros con {@code {"code":400,"message":"No data is
   * available on the specified dates…"}} aunque los datos existan. Se usa únicamente
   * {@code outputsize} para abarcar el rango deseado; los puntos fuera del rango se descartan
   * en {@link #extractSeries} porque simplemente no están en la ventana pedida.
   */
  @SuppressWarnings("unchecked")
  private Map<String, Object> callTimeSeries(
      String symbol, String interval, int outputSize, LocalDate from, LocalDate to) {
    String url =
        "%s/time_series?symbol=%s&interval=%s&outputsize=%d&apikey=%s"
            .formatted(baseUrl, symbol.trim().toUpperCase(), interval, outputSize, apiKey);

    return webClient
        .get()
        .uri(url)
        .retrieve()
        .onStatus(
            status -> status.value() == 401 || status.value() == 403,
            response ->
                response
                    .bodyToMono(String.class)
                    .map(
                        body -> {
                          log.error(
                              "TwelveData /time_series portfolio: API key invalida o expirada"
                                  + " (HTTP {}). Verifique la variable TWELVEDATA_API_KEY."
                                  + " Detalle: {}",
                              response.statusCode().value(),
                              body);
                          return new MarketDataServerException(
                              "API key de Twelve Data invalida o expirada (HTTP "
                                  + response.statusCode().value()
                                  + ")");
                        })
                    .switchIfEmpty(
                        reactor.core.publisher.Mono.fromCallable(
                            () -> {
                              log.error(
                                  "TwelveData /time_series portfolio: API key invalida o"
                                      + " expirada (HTTP {}). Verifique TWELVEDATA_API_KEY.",
                                  response.statusCode().value());
                              return new MarketDataServerException(
                                  "API key de Twelve Data invalida o expirada (HTTP "
                                      + response.statusCode().value()
                                      + ")");
                            })))
        .onStatus(
            status -> status.value() == 429,
            response ->
                response
                    .bodyToMono(String.class)
                    .map(body -> new MarketDataRateLimitException("Twelve Data rate limit: " + body))
                    .switchIfEmpty(
                        reactor.core.publisher.Mono.just(
                            new MarketDataRateLimitException("Twelve Data rate limit HTTP 429"))))
        .onStatus(
            status -> status.value() == 404,
            response ->
                reactor.core.publisher.Mono.just(
                    new UnknownAssetSymbolException(
                        "Simbolo no encontrado en Twelve Data: " + symbol)))
        .onStatus(
            status ->
                status.is4xxClientError()
                    && status.value() != 401
                    && status.value() != 403
                    && status.value() != 404
                    && status.value() != 429,
            response ->
                response
                    .bodyToMono(String.class)
                    .map(
                        body ->
                            new MarketDataServerException(
                                "Twelve Data HTTP "
                                    + response.statusCode().value()
                                    + ": "
                                    + body))
                    .switchIfEmpty(
                        reactor.core.publisher.Mono.just(
                            new MarketDataServerException(
                                "Twelve Data HTTP " + response.statusCode().value()))))
        .onStatus(
            status -> status.is5xxServerError(),
            response ->
                response
                    .bodyToMono(String.class)
                    .map(
                        body ->
                            new MarketDataServerException(
                                "Twelve Data error de servidor (HTTP "
                                    + response.statusCode().value()
                                    + "): "
                                    + body))
                    .switchIfEmpty(
                        reactor.core.publisher.Mono.just(
                            new MarketDataServerException(
                                "Twelve Data error de servidor (HTTP "
                                    + response.statusCode().value()
                                    + ")"))))
        .bodyToMono(Map.class)
        .map(Map.class::cast)
        .onErrorMap(
            WebClientRequestException.class,
            ex -> {
              log.error(
                  "TwelveData /time_series: error de red/timeout para {} interval={}",
                  symbol,
                  interval,
                  ex);
              return new MarketDataServerException(
                  "Error de conectividad con Twelve Data para el simbolo: " + symbol);
            })
        .doOnError(
            ex ->
                log.error(
                    "Error TwelveData /time_series symbol={} interval={}", symbol, interval, ex))
        .block();
  }

  // -------------------------------------------------------------------------
  // Extracción y mapeo de la respuesta
  // -------------------------------------------------------------------------

  @SuppressWarnings("unchecked")
  private List<PricePoint> extractSeries(Map<String, Object> json, Resolution resolution) {
    if (json == null) {
      return List.of();
    }

    throwIfBodyContainsError(json);

    Object rawValues = json.get("values");
    if (!(rawValues instanceof List<?> values)) {
      return List.of();
    }

    ZoneId zone =
        Optional.ofNullable(json.get("meta"))
            .filter(Map.class::isInstance)
            .map(m -> (Map<String, Object>) m)
            .map(meta -> meta.get("exchange_timezone"))
            .map(tz -> safeZone(String.valueOf(tz)))
            .orElse(DEFAULT_EXCHANGE_ZONE);

    return values.stream()
        .filter(Map.class::isInstance)
        .map(Map.class::cast)
        .map(entry -> toPoint(entry, resolution, zone))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .sorted(Comparator.comparing(PricePoint::time))
        .toList();
  }

  private Optional<PricePoint> toPoint(Map<?, ?> entry, Resolution resolution, ZoneId zone) {
    Object datetimeRaw = entry.get("datetime");
    Object closeRaw = entry.get("close");

    if (datetimeRaw == null || closeRaw == null) {
      return Optional.empty();
    }

    String datetime = String.valueOf(datetimeRaw).trim();
    String closeStr = String.valueOf(closeRaw).trim();

    if (datetime.isEmpty() || closeStr.isEmpty()) {
      return Optional.empty();
    }

    try {
      Instant instant = parseDateTime(datetime, resolution, zone);
      BigDecimal price = new BigDecimal(closeStr);
      return Optional.of(new PricePoint(instant, price));
    } catch (DateTimeParseException | NumberFormatException ex) {
      log.warn(
          "TwelveData: no se pudo parsear entry datetime={} close={}", datetime, closeStr, ex);
      return Optional.empty();
    }
  }

  /**
   * Parsea el campo {@code datetime} de Twelve Data:
   *
   * <ul>
   *   <li>Diario: {@code "2026-05-22"} → medianoche UTC
   *   <li>Intraday: {@code "2026-05-22 15:30:00"} → instante en el timezone del exchange
   * </ul>
   */
  private Instant parseDateTime(String datetime, Resolution resolution, ZoneId zone) {
    if (resolution == Resolution.INTRADAY && datetime.length() > 10) {
      return LocalDateTime.parse(datetime, INTRADAY_FMT).atZone(zone).toInstant();
    }
    String datePart = datetime.length() > 10 ? datetime.substring(0, 10) : datetime;
    return LocalDate.parse(datePart).atStartOfDay().toInstant(ZoneOffset.UTC);
  }

  // -------------------------------------------------------------------------
  // Manejo de errores en body (Twelve Data retorna status:"error" con HTTP 200)
  // -------------------------------------------------------------------------

  /**
   * Lanza la excepción correcta cuando el body HTTP 200 contiene {@code "status":"error"}.
   *
   * <p>Twelve Data usa el mismo body code {@code 400} para dos categorías distintas:
   *
   * <ul>
   *   <li><b>Símbolo inválido</b> (mensaje contiene "is not valid", "not found", etc.) →
   *       {@link UnknownAssetSymbolException}: detiene el fallback.
   *   <li><b>Sin datos para esas fechas</b> (mensaje contiene "no data" o "date") →
   *       {@link MarketDataServerException}: permite fallback al siguiente proveedor.
   * </ul>
   *
   * <p>Body code {@code 404} siempre indica símbolo inexistente.
   */
  private void throwIfBodyContainsError(Map<String, Object> json) {
    if (!"error".equalsIgnoreCase(String.valueOf(json.get("status")))) {
      return;
    }

    int code = parseCode(json.get("code"));
    String message =
        String.valueOf(json.getOrDefault("message", "Error desconocido de Twelve Data"));

    switch (code) {
      case 429 -> throw new MarketDataRateLimitException("Twelve Data rate limit: " + message);
      case 404 -> throw new UnknownAssetSymbolException(message);
      case 400 -> {
        if (isSymbolNotFoundMessage(message)) {
          throw new UnknownAssetSymbolException(message);
        }
        log.debug(
            "TwelveData body error 400 (no es simbolo invalido, se permite fallback): {}",
            message);
        throw new MarketDataServerException("Twelve Data sin datos (code=400): " + message);
      }
      case 401, 403 -> {
        log.error(
            "TwelveData body error {}: API key invalida o expirada."
                + " Verifique la variable TWELVEDATA_API_KEY. Detalle: {}",
            code,
            message);
        throw new MarketDataServerException(
            "API key de Twelve Data invalida o expirada (body code " + code + "): " + message);
      }
      default -> throw new MarketDataServerException(
          "Twelve Data error inesperado (code=" + code + "): " + message);
    }
  }

  /**
   * Determina si el mensaje de Twelve Data indica símbolo inexistente.
   *
   * <p>Twelve Data usa mensajes como {@code "**IBM** is not valid."} para símbolos inexistentes,
   * mientras que {@code "No data is available on the specified dates"} indica un problema de rango
   * donde el símbolo sí existe.
   */
  private boolean isSymbolNotFoundMessage(String message) {
    if (message == null) return false;
    String lower = message.toLowerCase();
    if (lower.contains("symbol not found")
        || lower.contains("not found")
        || lower.contains("is not supported")
        || lower.contains("invalid symbol")
        || lower.contains("is not valid")) {
      return true;
    }
    if (lower.contains("no data is available")
        || lower.contains("specified dates")
        || lower.contains("start_date")
        || lower.contains("end_date")
        || lower.contains("out of range")) {
      return false;
    }
    return false;
  }

  private int parseCode(Object raw) {
    if (raw instanceof Number n) return n.intValue();
    try {
      return Integer.parseInt(String.valueOf(raw));
    } catch (NumberFormatException ignored) {
      return -1;
    }
  }

  // -------------------------------------------------------------------------
  // Resolución del intervalo
  // -------------------------------------------------------------------------

  private String resolveInterval(HoldingsHistoryRange range) {
    if (range.resolution() == Resolution.INTRADAY) {
      return resolveIntradayInterval(range.stepSeconds());
    }
    return "1day";
  }

  private String resolveIntradayInterval(long stepSeconds) {
    if (stepSeconds <= 60) return "1min";
    if (stepSeconds <= 300) return "5min";
    if (stepSeconds <= 900) return "15min";
    if (stepSeconds <= 1800) return "30min";
    if (stepSeconds <= 3600) return "1h";
    if (stepSeconds <= 7200) return "2h";
    if (stepSeconds <= 14400) return "4h";
    return "8h";
  }

  private String resolveIntervalFromDuration(Duration interval) {
    if (interval == null) return "1day";
    return resolveIntradayInterval(interval.getSeconds());
  }

  private int pointsPerDay(String interval) {
    return switch (interval) {
      case "1min" -> 390;
      case "5min" -> 78;
      case "15min" -> 26;
      case "30min" -> 13;
      case "45min" -> 9;
      case "1h" -> 7;
      case "2h" -> 4;
      case "4h" -> 2;
      case "8h" -> 1;
      default -> 1;
    };
  }

  private int resolveOutputSize(HoldingsHistoryRange range, String interval) {
    if (range.isAll()) {
      return MAX_OUTPUT_SIZE;
    }
    int estimated = (int) (range.days() * pointsPerDay(interval) * 1.2) + 1;
    return Math.min(estimated, MAX_OUTPUT_SIZE);
  }

  private ZoneId safeZone(String tz) {
    try {
      return ZoneId.of(tz);
    } catch (Exception ignored) {
      return DEFAULT_EXCHANGE_ZONE;
    }
  }
}
