package com.mx.cryptomonitor.marketdata.infrastructure.outbound.databursatil;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import com.mx.cryptomonitor.marketdata.application.port.out.BmvMarketDataPort;
import com.mx.cryptomonitor.marketdata.domain.exception.DataBursatilException;
import com.mx.cryptomonitor.marketdata.domain.exception.DataBursatilInvalidSymbolException;
import com.mx.cryptomonitor.marketdata.domain.exception.DataBursatilRateLimitException;
import com.mx.cryptomonitor.marketdata.domain.exception.ExternalProviderInvalidSymbolException;
import com.mx.cryptomonitor.marketdata.domain.exception.ExternalProviderRateLimitException;
import com.mx.cryptomonitor.marketdata.domain.exception.ExternalProviderUpstreamException;
import com.mx.cryptomonitor.marketdata.domain.model.BmvFxQuote;
import com.mx.cryptomonitor.marketdata.domain.model.BmvHistoricalPoint;
import com.mx.cryptomonitor.marketdata.domain.model.BmvIntradayPoint;
import com.mx.cryptomonitor.marketdata.domain.model.BmvQuote;
import com.mx.cryptomonitor.marketdata.infrastructure.configuration.DataBursatilProperties;

import lombok.extern.slf4j.Slf4j;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;

/**
 * Adaptador para DataBursatil (https://api.databursatil.com) — cotizaciones y precios historicos de
 * la Bolsa Mexicana de Valores (BMV).
 */
@Component
@Slf4j
public class DataBursatilAdapter implements BmvMarketDataPort {

  private static final int MAX_BATCH_SIZE = 50;
  private static final String CONCEPTOS = "U,P,A,X,N,C,M,V,O,I";
  private static final Duration TIMEOUT = Duration.ofSeconds(10);

  private static final ParameterizedTypeReference<Map<String, Map<String, BmvQuote>>> QUOTES_TYPE =
      new ParameterizedTypeReference<>() {};
  private static final ParameterizedTypeReference<Map<String, List<BigDecimal>>> HISTORY_TYPE =
      new ParameterizedTypeReference<>() {};
  private static final ParameterizedTypeReference<Map<String, BigDecimal>> INTRADAY_TYPE =
      new ParameterizedTypeReference<>() {};
  private static final ParameterizedTypeReference<Map<String, Object>> FX_TYPE =
      new ParameterizedTypeReference<>() {};

  private final WebClient webClient;
  private final String token;

  @Autowired
  public DataBursatilAdapter(
      @Qualifier("databursatilWebClient") WebClient webClient, DataBursatilProperties properties) {
    this.webClient = webClient;
    this.token = properties.token();
  }

  /** /v2/cotizaciones — batch de hasta {@value #MAX_BATCH_SIZE} emisoras en una sola peticion. */
  @Override
  public Mono<Map<String, BmvQuote>> getQuotes(List<String> symbols, String bolsa) {
    if (symbols == null || symbols.isEmpty()) {
      return Mono.just(Map.of());
    }
    if (symbols.size() > MAX_BATCH_SIZE) {
      throw new IllegalArgumentException(
          "DataBursatil admite maximo "
              + MAX_BATCH_SIZE
              + " emisoras por peticion, se recibieron "
              + symbols.size());
    }
    String emisoraSerie = String.join(",", symbols);
    String bolsaKey = bolsa.toLowerCase(Locale.ROOT);

    return mapErrors(
            webClient
                .get()
                .uri(
                    uriBuilder ->
                        uriBuilder
                            .path("/v2/cotizaciones")
                            .queryParam("emisora_serie", emisoraSerie)
                            .queryParam("bolsa", bolsa)
                            .queryParam("concepto", CONCEPTOS)
                            .queryParam("token", token)
                            .build())
                .retrieve(),
            QUOTES_TYPE,
            "cotizaciones " + emisoraSerie)
        .map(root -> extractQuotes(root, bolsaKey));
  }

  /** /v2/historicos — precios historicos de una emisora en un rango de fechas. */
  @Override
  public Mono<Map<LocalDate, BmvHistoricalPoint>> getHistory(
      String symbol, LocalDate inicio, LocalDate finalDate) {
    return mapErrors(
            webClient
                .get()
                .uri(
                    uriBuilder ->
                        uriBuilder
                            .path("/v2/historicos")
                            .queryParam("emisora_serie", symbol)
                            .queryParam("fecha_inicio", inicio)
                            .queryParam("fecha_final", finalDate)
                            .queryParam("token", token)
                            .build())
                .retrieve(),
            HISTORY_TYPE,
            "historicos " + symbol)
        .map(this::mapHistory);
  }

  /** /v2/intradia — velas por hora del dia en curso. */
  @Override
  public Mono<List<BmvIntradayPoint>> getIntraday(String symbol) {
    return mapErrors(
            webClient
                .get()
                .uri(
                    uriBuilder ->
                        uriBuilder
                            .path("/v2/intradia")
                            .queryParam("emisora_serie", symbol)
                            .queryParam("token", token)
                            .build())
                .retrieve(),
            INTRADAY_TYPE,
            "intradia " + symbol)
        .map(this::mapIntraday);
  }

  /** /v2/divisas — tipo de cambio. */
  @Override
  public Mono<BmvFxQuote> getFxRate(String ticker) {
    return mapErrors(
            webClient
                .get()
                .uri(
                    uriBuilder ->
                        uriBuilder
                            .path("/v2/divisas")
                            .queryParam("ticker", ticker)
                            .queryParam("token", token)
                            .build())
                .retrieve(),
            FX_TYPE,
            "divisas " + ticker)
        .map(root -> mapFxQuote(root, ticker));
  }

  private Map<String, BmvQuote> extractQuotes(
      Map<String, Map<String, BmvQuote>> root, String bolsaKey) {
    if (root == null) {
      return Map.of();
    }
    Map<String, BmvQuote> result = new LinkedHashMap<>();
    root.forEach(
        (emisora, byBolsa) -> {
          if (byBolsa == null) {
            return;
          }
          BmvQuote quote = byBolsa.get(bolsaKey);
          if (quote != null) {
            result.put(emisora, quote);
          }
        });
    return result;
  }

  private Map<LocalDate, BmvHistoricalPoint> mapHistory(Map<String, List<BigDecimal>> raw) {
    if (raw == null) {
      return Map.of();
    }
    Map<LocalDate, BmvHistoricalPoint> result = new TreeMap<>();
    raw.forEach(
        (dateStr, values) -> {
          if (values == null || values.size() < 2) {
            return;
          }
          LocalDate date = LocalDate.parse(dateStr);
          result.put(date, new BmvHistoricalPoint(date, values.get(0), values.get(1)));
        });
    return result;
  }

  private List<BmvIntradayPoint> mapIntraday(Map<String, BigDecimal> raw) {
    if (raw == null) {
      return List.of();
    }
    return raw.entrySet().stream()
        .map(entry -> new BmvIntradayPoint(entry.getKey(), entry.getValue()))
        .sorted(Comparator.comparing(BmvIntradayPoint::time))
        .toList();
  }

  private BmvFxQuote mapFxQuote(Map<String, Object> root, String ticker) {
    if (!(root.get(ticker) instanceof Map<?, ?> inner)) {
      throw new DataBursatilInvalidSymbolException(
          "DataBursatil no devolvio cotizacion para " + ticker);
    }
    double u = toDouble(inner.get("u"));
    double c = toDouble(inner.get("c"));
    double m = toDouble(inner.get("m"));
    String t = String.valueOf(root.getOrDefault("t", ""));
    return new BmvFxQuote(u, c, m, t);
  }

  private double toDouble(Object value) {
    return value == null ? 0.0 : Double.parseDouble(String.valueOf(value));
  }

  /** Traduce codigos HTTP y errores de red de DataBursatil a la jerarquia de excepciones. */
  private <T> Mono<T> mapErrors(
      WebClient.ResponseSpec spec, ParameterizedTypeReference<T> bodyType, String context) {
    return spec.onStatus(
            status -> status.value() == 429,
            response ->
                response
                    .bodyToMono(String.class)
                    .defaultIfEmpty("DataBursatil rate limit exceeded")
                    .flatMap(
                        body ->
                            Mono.error(new DataBursatilRateLimitException(context + ": " + body))))
        .onStatus(
            status -> status.value() == 404,
            response ->
                response
                    .bodyToMono(String.class)
                    .defaultIfEmpty("DataBursatil symbol not found")
                    .flatMap(
                        body ->
                            Mono.error(
                                new DataBursatilInvalidSymbolException(context + ": " + body))))
        .onStatus(
            HttpStatusCode::isError,
            response ->
                response
                    .bodyToMono(String.class)
                    .defaultIfEmpty("DataBursatil error")
                    .flatMap(
                        body ->
                            Mono.error(
                                new DataBursatilException(
                                    "Error HTTP "
                                        + response.statusCode().value()
                                        + " de DataBursatil ("
                                        + context
                                        + "): "
                                        + body))))
        .bodyToMono(bodyType)
        .timeout(TIMEOUT)
        .onErrorMap(
            WebClientRequestException.class,
            ex -> {
              log.error("DataBursatil: error de red/timeout ({})", context, ex);
              return new DataBursatilException(
                  "Error de conectividad con DataBursatil (" + context + ")", ex);
            })
        .onErrorMap(
            ex -> !isKnownProviderException(ex),
            ex -> {
              log.error("Error inesperado DataBursatil ({})", context, ex);
              return new DataBursatilException(
                  "No se pudo consultar DataBursatil (" + context + ")", ex);
            });
  }

  private boolean isKnownProviderException(Throwable ex) {
    Throwable unwrapped = Exceptions.unwrap(ex);
    return unwrapped instanceof ExternalProviderRateLimitException
        || unwrapped instanceof ExternalProviderInvalidSymbolException
        || unwrapped instanceof ExternalProviderUpstreamException;
  }
}
