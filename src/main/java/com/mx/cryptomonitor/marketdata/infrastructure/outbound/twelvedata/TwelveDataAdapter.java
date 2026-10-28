package com.mx.cryptomonitor.marketdata.infrastructure.outbound.twelvedata;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import com.mx.cryptomonitor.marketdata.application.port.out.StockQuoteProvider;
import com.mx.cryptomonitor.marketdata.application.port.out.StockTimeSeriesPort;
import com.mx.cryptomonitor.marketdata.domain.exception.ExternalProviderInvalidSymbolException;
import com.mx.cryptomonitor.marketdata.domain.exception.ExternalProviderRateLimitException;
import com.mx.cryptomonitor.marketdata.domain.exception.ExternalProviderUpstreamException;
import com.mx.cryptomonitor.marketdata.domain.exception.TwelveDataAuthException;
import com.mx.cryptomonitor.marketdata.domain.exception.TwelveDataException;
import com.mx.cryptomonitor.marketdata.domain.exception.TwelveDataInvalidSymbolException;
import com.mx.cryptomonitor.marketdata.domain.exception.TwelveDataRateLimitException;
import com.mx.cryptomonitor.marketdata.domain.model.StockInterval;
import com.mx.cryptomonitor.marketdata.domain.model.StockTimeSeries;
import com.mx.cryptomonitor.marketdata.domain.model.StockTimeSeriesPoint;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Exceptions;

/**
 * Adaptador para Twelve Data (https://twelvedata.com).
 *
 * <p>Manejo de errores:
 *
 * <ul>
 *   <li>HTTP 401 / 403 o body con code 401/403 → {@link TwelveDataAuthException}: la API key expiró
 *       o es inválida. Se loguea como ERROR y el orquestador hace fallback al siguiente proveedor.
 *   <li>HTTP 429 o body con code 429 → {@link TwelveDataRateLimitException}: límite de peticiones
 *       alcanzado. Fallback al siguiente proveedor.
 *   <li>HTTP 404 o body con code 400/404 → {@link TwelveDataInvalidSymbolException}: símbolo no
 *       existe. El orquestador detiene el fallback.
 *   <li>HTTP 5xx o body con otro code → {@link TwelveDataException}: error temporal del proveedor.
 *       Fallback al siguiente proveedor.
 *   <li>Error de red / timeout → {@link TwelveDataException}: problema de conectividad. Fallback.
 * </ul>
 *
 * <p>Plan gratuito: 800 req/día, 8 req/min.
 */
@RequiredArgsConstructor
@Slf4j
public class TwelveDataAdapter implements StockQuoteProvider, StockTimeSeriesPort {

  private static final String PROVIDER = "twelvedata";

  private final WebClient webClient;
  private final String baseUrl;
  private final String apiKey;

  @Override
  public String providerName() {
    return PROVIDER;
  }

  // -------------------------------------------------------------------------
  // getLatest — endpoint /price
  // -------------------------------------------------------------------------

  @Override
  public Optional<BigDecimal> getLatest(String symbol) {
    log.debug("TwelveData /price para {}", symbol);

    String url = "%s/price?symbol=%s&apikey=%s".formatted(baseUrl, symbol, apiKey);

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
                              "TwelveData /price: API key invalida o expirada (HTTP {}). "
                                  + "Verifique la variable TWELVEDATA_API_KEY. Detalle: {}",
                              response.statusCode().value(),
                              body);
                          return new TwelveDataAuthException(
                              "API key de TwelveData invalida o expirada (HTTP "
                                  + response.statusCode().value()
                                  + ")");
                        })
                    .switchIfEmpty(
                        reactor.core.publisher.Mono.fromCallable(
                            () -> {
                              log.error(
                                  "TwelveData /price: API key invalida o expirada (HTTP {})."
                                      + " Verifique la variable TWELVEDATA_API_KEY.",
                                  response.statusCode().value());
                              return new TwelveDataAuthException(
                                  "API key de TwelveData invalida o expirada (HTTP "
                                      + response.statusCode().value()
                                      + ")");
                            })))
        .onStatus(
            status -> status.value() == 429,
            response ->
                response
                    .bodyToMono(String.class)
                    .map(
                        body -> new TwelveDataRateLimitException("HTTP 429 de TwelveData: " + body))
                    .switchIfEmpty(
                        reactor.core.publisher.Mono.just(
                            new TwelveDataRateLimitException("HTTP 429 de TwelveData"))))
        .onStatus(
            status -> status.value() == 404,
            response ->
                reactor.core.publisher.Mono.just(
                    new TwelveDataInvalidSymbolException(
                        "Simbolo no encontrado en TwelveData: " + symbol)))
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
                            new TwelveDataException(
                                "Error HTTP "
                                    + response.statusCode().value()
                                    + " de TwelveData: "
                                    + body))
                    .switchIfEmpty(
                        reactor.core.publisher.Mono.just(
                            new TwelveDataException(
                                "Error HTTP " + response.statusCode().value() + " de TwelveData"))))
        .onStatus(
            status -> status.is5xxServerError(),
            response ->
                response
                    .bodyToMono(String.class)
                    .map(
                        body ->
                            new TwelveDataException(
                                "Error de servidor TwelveData (HTTP "
                                    + response.statusCode().value()
                                    + "): "
                                    + body))
                    .switchIfEmpty(
                        reactor.core.publisher.Mono.just(
                            new TwelveDataException(
                                "Error de servidor TwelveData (HTTP "
                                    + response.statusCode().value()
                                    + ")"))))
        .bodyToMono(Map.class)
        .map(this::extractPrice)
        .onErrorMap(
            WebClientRequestException.class,
            ex -> {
              log.error("TwelveData /price: error de red/timeout para {}", symbol, ex);
              return new TwelveDataException(
                  "Error de conectividad con TwelveData para el simbolo: " + symbol, ex);
            })
        .onErrorMap(
            ex -> !isKnownProviderException(ex),
            ex -> {
              log.error("Error inesperado TwelveData /price para {}", symbol, ex);
              return new TwelveDataException(
                  "No se pudo consultar TwelveData para el simbolo: " + symbol, ex);
            })
        .block();
  }

  @SuppressWarnings("unchecked")
  private Optional<BigDecimal> extractPrice(Map<String, Object> json) {
    throwIfBodyContainsError(json);

    Object price = json.get("price");
    if (price == null) {
      return Optional.empty();
    }
    String raw = String.valueOf(price).trim();
    if (raw.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new BigDecimal(raw));
  }

  // -------------------------------------------------------------------------
  // getHistorical — endpoint /time_series
  // -------------------------------------------------------------------------

  @Override
  public Optional<BigDecimal> getHistorical(String symbol, LocalDate date) {
    log.debug("TwelveData /time_series para {} en {}", symbol, date);

    // El plan gratuito de Twelve Data no admite start_date/end_date en /time_series.
    // En su lugar pedimos los últimos N días de velas diarias y buscamos la fecha
    // solicitada en el resultado (client-side). Se añade un buffer de ~10 entradas
    // para absorber fines de semana y festivos que no generan vela.
    long calendarDaysBack = ChronoUnit.DAYS.between(date, LocalDate.now());
    int outputSize = (int) Math.min(Math.max(calendarDaysBack + 10, 10), 5000);

    String url =
        "%s/time_series?symbol=%s&interval=1day&outputsize=%d&apikey=%s"
            .formatted(baseUrl, symbol, outputSize, apiKey);

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
                              "TwelveData /time_series: API key invalida o expirada (HTTP {})."
                                  + " Verifique la variable TWELVEDATA_API_KEY. Detalle: {}",
                              response.statusCode().value(),
                              body);
                          return new TwelveDataAuthException(
                              "API key de TwelveData invalida o expirada (HTTP "
                                  + response.statusCode().value()
                                  + ")");
                        })
                    .switchIfEmpty(
                        reactor.core.publisher.Mono.fromCallable(
                            () -> {
                              log.error(
                                  "TwelveData /time_series: API key invalida o expirada (HTTP {}).",
                                  response.statusCode().value());
                              return new TwelveDataAuthException(
                                  "API key de TwelveData invalida o expirada (HTTP "
                                      + response.statusCode().value()
                                      + ")");
                            })))
        .onStatus(
            status -> status.value() == 429,
            response ->
                response
                    .bodyToMono(String.class)
                    .map(
                        body -> new TwelveDataRateLimitException("HTTP 429 de TwelveData: " + body))
                    .switchIfEmpty(
                        reactor.core.publisher.Mono.just(
                            new TwelveDataRateLimitException("HTTP 429 de TwelveData"))))
        .onStatus(
            status -> status.value() == 404,
            response ->
                reactor.core.publisher.Mono.just(
                    new TwelveDataInvalidSymbolException(
                        "Simbolo no encontrado en TwelveData: " + symbol)))
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
                            new TwelveDataException(
                                "Error HTTP "
                                    + response.statusCode().value()
                                    + " de TwelveData: "
                                    + body))
                    .switchIfEmpty(
                        reactor.core.publisher.Mono.just(
                            new TwelveDataException(
                                "Error HTTP " + response.statusCode().value() + " de TwelveData"))))
        .onStatus(
            status -> status.is5xxServerError(),
            response ->
                response
                    .bodyToMono(String.class)
                    .map(
                        body ->
                            new TwelveDataException(
                                "Error de servidor TwelveData (HTTP "
                                    + response.statusCode().value()
                                    + "): "
                                    + body))
                    .switchIfEmpty(
                        reactor.core.publisher.Mono.just(
                            new TwelveDataException(
                                "Error de servidor TwelveData (HTTP "
                                    + response.statusCode().value()
                                    + ")"))))
        .bodyToMono(Map.class)
        .map(json -> extractHistorical(json, date))
        .onErrorMap(
            WebClientRequestException.class,
            ex -> {
              log.error(
                  "TwelveData /time_series: error de red/timeout para {} en {}", symbol, date, ex);
              return new TwelveDataException(
                  "Error de conectividad con TwelveData para el simbolo: " + symbol, ex);
            })
        .onErrorMap(
            ex -> !isKnownProviderException(ex),
            ex -> {
              log.error("Error inesperado TwelveData /time_series para {} en {}", symbol, date, ex);
              return new TwelveDataException(
                  "No se pudo consultar TwelveData historico para el simbolo: " + symbol, ex);
            })
        .block();
  }

  @SuppressWarnings("unchecked")
  private Optional<BigDecimal> extractHistorical(Map<String, Object> json, LocalDate date) {
    throwIfBodyContainsError(json);

    Object rawValues = json.get("values");
    if (!(rawValues instanceof List<?> values) || values.isEmpty()) {
      return Optional.empty();
    }

    // Twelve Data devuelve velas descendentes (más reciente primero).
    // Buscamos la entrada cuyo datetime coincide exactamente con la fecha pedida.
    // Los días sin mercado (fines de semana, festivos) no tienen vela → Optional.empty().
    String dateStr = date.toString(); // "yyyy-MM-dd"
    for (Object item : values) {
      if (!(item instanceof Map<?, ?> entry)) continue;
      Object datetimeRaw = entry.get("datetime");
      String datetime = datetimeRaw == null ? "" : String.valueOf(datetimeRaw);
      if (!datetime.startsWith(dateStr)) continue;

      Object close = entry.get("close");
      if (close == null) return Optional.empty();
      return Optional.of(new BigDecimal(String.valueOf(close).trim()));
    }

    log.debug(
        "TwelveData no encontro vela para {} en la fecha {}. Puede ser fin de semana o festivo.",
        date,
        date);
    return Optional.empty();
  }

  // -------------------------------------------------------------------------
  // Manejo de errores en el body (HTTP 200 con status:"error")
  // -------------------------------------------------------------------------
  // getTimeSeries — endpoint /time_series con intervalo configurable
  // -------------------------------------------------------------------------

  @Override
  @SuppressWarnings("unchecked")
  public StockTimeSeries getTimeSeries(String symbol, StockInterval interval, int outputSize) {
    log.debug(
        "TwelveData /time_series symbol={} interval={} outputSize={}",
        symbol,
        interval.getApiCode(),
        outputSize);

    String url =
        "%s/time_series?symbol=%s&interval=%s&outputsize=%d&apikey=%s"
            .formatted(
                baseUrl,
                symbol.trim().toUpperCase(),
                interval.getApiCode(),
                Math.max(1, Math.min(outputSize, 5000)),
                apiKey);

    Map<String, Object> json =
        webClient
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
                                  "TwelveData /time_series: API key invalida o expirada (HTTP {})."
                                      + " Verifique TWELVEDATA_API_KEY. Detalle: {}",
                                  response.statusCode().value(),
                                  body);
                              return new TwelveDataAuthException(
                                  "API key de TwelveData invalida o expirada (HTTP "
                                      + response.statusCode().value()
                                      + ")");
                            })
                        .switchIfEmpty(
                            reactor.core.publisher.Mono.fromCallable(
                                () -> {
                                  log.error(
                                      "TwelveData /time_series: API key invalida o expirada"
                                          + " (HTTP {}). Verifique TWELVEDATA_API_KEY.",
                                      response.statusCode().value());
                                  return new TwelveDataAuthException(
                                      "API key de TwelveData invalida o expirada (HTTP "
                                          + response.statusCode().value()
                                          + ")");
                                })))
            .onStatus(
                status -> status.value() == 429,
                response ->
                    response
                        .bodyToMono(String.class)
                        .map(
                            body ->
                                new TwelveDataRateLimitException("HTTP 429 de TwelveData: " + body))
                        .switchIfEmpty(
                            reactor.core.publisher.Mono.just(
                                new TwelveDataRateLimitException("HTTP 429 de TwelveData"))))
            .onStatus(
                status -> status.value() == 404,
                response ->
                    reactor.core.publisher.Mono.just(
                        new TwelveDataInvalidSymbolException(
                            "Simbolo no encontrado en TwelveData: " + symbol)))
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
                                new TwelveDataException(
                                    "Error HTTP "
                                        + response.statusCode().value()
                                        + " de TwelveData: "
                                        + body))
                        .switchIfEmpty(
                            reactor.core.publisher.Mono.just(
                                new TwelveDataException(
                                    "Error HTTP "
                                        + response.statusCode().value()
                                        + " de TwelveData"))))
            .onStatus(
                status -> status.is5xxServerError(),
                response ->
                    response
                        .bodyToMono(String.class)
                        .map(
                            body ->
                                new TwelveDataException(
                                    "Error de servidor TwelveData (HTTP "
                                        + response.statusCode().value()
                                        + "): "
                                        + body))
                        .switchIfEmpty(
                            reactor.core.publisher.Mono.just(
                                new TwelveDataException(
                                    "Error de servidor TwelveData (HTTP "
                                        + response.statusCode().value()
                                        + ")"))))
            .bodyToMono(Map.class)
            .map(Map.class::cast)
            .onErrorMap(
                WebClientRequestException.class,
                ex -> {
                  log.error(
                      "TwelveData /time_series: error de red/timeout symbol={} interval={}",
                      symbol,
                      interval.getApiCode(),
                      ex);
                  return new TwelveDataException(
                      "Error de conectividad con TwelveData para el simbolo: " + symbol, ex);
                })
            .onErrorMap(
                ex -> !isKnownProviderException(ex),
                ex -> {
                  log.error(
                      "Error inesperado TwelveData /time_series symbol={} interval={}",
                      symbol,
                      interval.getApiCode(),
                      ex);
                  return new TwelveDataException(
                      "No se pudo consultar TwelveData time_series para: " + symbol, ex);
                })
            .block();

    return buildTimeSeries(json, symbol, interval);
  }

  @SuppressWarnings("unchecked")
  private StockTimeSeries buildTimeSeries(
      Map<String, Object> json, String symbol, StockInterval interval) {
    throwIfBodyContainsError(json);

    // Extraer meta
    Map<String, Object> meta =
        json.get("meta") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    String currency = String.valueOf(meta.getOrDefault("currency", "USD"));
    String exchange = String.valueOf(meta.getOrDefault("exchange", ""));
    String timezone = String.valueOf(meta.getOrDefault("exchange_timezone", "America/New_York"));
    String type = String.valueOf(meta.getOrDefault("type", ""));

    // Extraer values
    Object rawValues = json.get("values");
    List<StockTimeSeriesPoint> points = new ArrayList<>();
    if (rawValues instanceof List<?> values) {
      for (Object item : values) {
        if (!(item instanceof Map<?, ?> entry)) continue;
        StockTimeSeriesPoint point = toTimeSeriesPoint(entry);
        if (point != null) points.add(point);
      }
    }

    // Ordenar ascendente por datetime (lexicográfico funciona para ISO 8601)
    points.sort(Comparator.comparing(StockTimeSeriesPoint::datetime));

    return new StockTimeSeries(
        symbol.trim().toUpperCase(), interval, currency, exchange, timezone, type, points);
  }

  private StockTimeSeriesPoint toTimeSeriesPoint(Map<?, ?> entry) {
    Object datetime = entry.get("datetime");
    Object open = entry.get("open");
    Object high = entry.get("high");
    Object low = entry.get("low");
    Object close = entry.get("close");
    Object volume = entry.get("volume");

    if (datetime == null || close == null) return null;

    try {
      return new StockTimeSeriesPoint(
          String.valueOf(datetime),
          open == null ? null : new BigDecimal(String.valueOf(open).trim()),
          high == null ? null : new BigDecimal(String.valueOf(high).trim()),
          low == null ? null : new BigDecimal(String.valueOf(low).trim()),
          new BigDecimal(String.valueOf(close).trim()),
          volume == null ? null : Long.parseLong(String.valueOf(volume).trim()));
    } catch (NumberFormatException ex) {
      log.warn("TwelveData: entry malformado, se omite. datetime={}", datetime);
      return null;
    }
  }

  // -------------------------------------------------------------------------

  /**
   * Lanza la excepción correcta cuando el body HTTP 200 contiene {@code "status":"error"}.
   *
   * <p>Twelve Data usa el mismo body code {@code 400} para dos categorías distintas:
   *
   * <ul>
   *   <li><b>Símbolo inválido</b> (mensaje contiene "symbol" o "not found") → {@link
   *       TwelveDataInvalidSymbolException}: detiene el fallback en el orquestador.
   *   <li><b>Sin datos para esas fechas</b> (mensaje contiene "no data" o "date") → {@link
   *       TwelveDataException}: permite fallback al siguiente proveedor.
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
        String.valueOf(json.getOrDefault("message", "Error desconocido de TwelveData"));

    switch (code) {
      case 429 -> throw new TwelveDataRateLimitException(message);
      case 404 -> throw new TwelveDataInvalidSymbolException(message);
      case 400 -> {
        // Twelve Data reutiliza code=400 para "símbolo no existe" Y "sin datos para esas fechas".
        // Solo se detiene el fallback cuando el mensaje indica claramente que el símbolo no existe.
        if (isSymbolNotFoundMessage(message)) {
          throw new TwelveDataInvalidSymbolException(message);
        }
        // "No data is available on the specified dates" u otros 400 → upstream error (fallback).
        log.debug(
            "TwelveData body error 400 (no es simbolo invalido, se permite fallback): {}", message);
        throw new TwelveDataException("TwelveData sin datos (code=400): " + message);
      }
      case 401, 403 -> {
        log.error(
            "TwelveData body error {}: API key invalida o expirada."
                + " Verifique la variable TWELVEDATA_API_KEY. Detalle: {}",
            code,
            message);
        throw new TwelveDataAuthException(
            "API key de TwelveData invalida o expirada (body code " + code + "): " + message);
      }
      default -> throw new TwelveDataException(
          "TwelveData error inesperado (code=" + code + "): " + message);
    }
  }

  /**
   * Determina si el mensaje de error de Twelve Data indica que el símbolo no existe.
   *
   * <p>Twelve Data usa mensajes como "Symbol not found" o "**symbol** is not supported" para
   * símbolos inexistentes, mientras que "No data is available on the specified dates" u "Output
   * size is out of range" son errores de rango/fecha donde el símbolo sí existe.
   */
  private boolean isSymbolNotFoundMessage(String message) {
    if (message == null) return false;
    String lower = message.toLowerCase();
    // Mensajes que indican inequívocamente símbolo inexistente
    // Twelve Data usa: "**SYMBOL** is not valid.", "Symbol not found", "is not supported"
    if (lower.contains("symbol not found")
        || lower.contains("not found")
        || lower.contains("is not supported")
        || lower.contains("invalid symbol")
        || lower.contains("is not valid")) {
      return true;
    }
    // Mensajes que indican problema de fechas/rango — NO es símbolo inválido
    if (lower.contains("no data is available")
        || lower.contains("specified dates")
        || lower.contains("start_date")
        || lower.contains("end_date")
        || lower.contains("out of range")) {
      return false;
    }
    // Por defecto, code=400 sin mensaje claro → tratar como upstream error (permitir fallback)
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

  private boolean isKnownProviderException(Throwable ex) {
    Throwable unwrapped = Exceptions.unwrap(ex);
    return unwrapped instanceof ExternalProviderRateLimitException
        || unwrapped instanceof ExternalProviderInvalidSymbolException
        || unwrapped instanceof ExternalProviderUpstreamException;
  }
}
