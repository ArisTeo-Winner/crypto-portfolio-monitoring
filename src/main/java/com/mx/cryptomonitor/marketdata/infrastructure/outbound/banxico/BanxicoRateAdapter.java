package com.mx.cryptomonitor.marketdata.infrastructure.outbound.banxico;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mx.cryptomonitor.marketdata.application.port.out.GovBondRatePort;
import com.mx.cryptomonitor.marketdata.domain.exception.BanxicoException;
import com.mx.cryptomonitor.marketdata.domain.exception.BanxicoRateLimitException;
import com.mx.cryptomonitor.marketdata.domain.exception.ExternalProviderInvalidSymbolException;
import com.mx.cryptomonitor.marketdata.domain.exception.ExternalProviderRateLimitException;
import com.mx.cryptomonitor.marketdata.domain.exception.ExternalProviderUpstreamException;

import lombok.extern.slf4j.Slf4j;
import reactor.core.Exceptions;
import reactor.core.publisher.Mono;

/**
 * Adaptador para Banxico SIE (https://www.banxico.org.mx/SieAPIRest) — curva de tasas CETES via el
 * endpoint batch /oportuno, en una sola peticion para los 5 plazos vigentes.
 */
@Component
@Slf4j
public class BanxicoRateAdapter implements GovBondRatePort {

  private static final Duration TIMEOUT = Duration.ofSeconds(10);
  private static final DateTimeFormatter BANXICO_DATE_FORMAT =
      DateTimeFormatter.ofPattern("dd/MM/yyyy");

  // Mapeo plazo (dias) -> idSerie Banxico SIE, confirmado por el equipo de datos.
  private static final Map<Integer, String> SERIES_BY_TERM =
      Map.of(
          28, "SF43936",
          91, "SF43939",
          182, "SF43942",
          364, "SF43945",
          728, "SF349785");

  private static final String SERIES_IDS = String.join(",", SERIES_BY_TERM.values());

  private static final ParameterizedTypeReference<BanxicoOportunoResponse> RESPONSE_TYPE =
      new ParameterizedTypeReference<>() {};

  private final WebClient webClient;

  @Autowired
  public BanxicoRateAdapter(@Qualifier("banxicoWebClient") WebClient webClient) {
    this.webClient = webClient;
  }

  /** /SieAPIRest/service/v1/series/{ids}/datos/oportuno — curva completa en una sola peticion. */
  @Override
  public Map<Integer, BigDecimal> getCetesCurve() {
    BanxicoOportunoResponse response = fetchOportuno();
    if (response == null || response.bmx() == null || response.bmx().series() == null) {
      return Map.of();
    }
    Map<String, Integer> termBySeries = invertSeriesByTerm();
    Map<Integer, BigDecimal> curve = new TreeMap<>();
    for (BanxicoSerie serie : response.bmx().series()) {
      Integer term = termBySeries.get(serie.idSerie());
      if (term == null) {
        continue;
      }
      latestDato(serie.datos()).ifPresent(dato -> curve.put(term, new BigDecimal(dato.dato())));
    }
    return curve;
  }

  @Override
  public Optional<BigDecimal> getCetesRate(int termDays) {
    return GovBondRatePort.nearestRate(getCetesCurve(), termDays);
  }

  private Map<String, Integer> invertSeriesByTerm() {
    Map<String, Integer> result = new HashMap<>();
    SERIES_BY_TERM.forEach((term, series) -> result.put(series, term));
    return result;
  }

  private Optional<BanxicoDato> latestDato(List<BanxicoDato> datos) {
    if (datos == null || datos.isEmpty()) {
      return Optional.empty();
    }
    return datos.stream()
        .max(Comparator.comparing(dato -> LocalDate.parse(dato.fecha(), BANXICO_DATE_FORMAT)));
  }

  private BanxicoOportunoResponse fetchOportuno() {
    return mapErrors(
            webClient
                .get()
                .uri(
                    uriBuilder ->
                        uriBuilder
                            .path("/SieAPIRest/service/v1/series/{ids}/datos/oportuno")
                            .build(SERIES_IDS))
                .retrieve())
        .block();
  }

  /** Traduce codigos HTTP y errores de red de Banxico a la jerarquia de excepciones. */
  private Mono<BanxicoOportunoResponse> mapErrors(WebClient.ResponseSpec spec) {
    return spec.onStatus(
            status -> status.value() == 429,
            response ->
                response
                    .bodyToMono(String.class)
                    .defaultIfEmpty("Banxico rate limit exceeded")
                    .flatMap(
                        body -> Mono.error(new BanxicoRateLimitException("oportuno: " + body))))
        .onStatus(
            HttpStatusCode::isError,
            response ->
                response
                    .bodyToMono(String.class)
                    .defaultIfEmpty("Banxico error")
                    .flatMap(
                        body ->
                            Mono.error(
                                new BanxicoException(
                                    "Error HTTP "
                                        + response.statusCode().value()
                                        + " de Banxico (oportuno): "
                                        + body))))
        .bodyToMono(RESPONSE_TYPE)
        .timeout(TIMEOUT)
        .onErrorMap(
            WebClientRequestException.class,
            ex -> {
              log.error("Banxico: error de red/timeout (oportuno)", ex);
              return new BanxicoException("Error de conectividad con Banxico (oportuno)", ex);
            })
        .onErrorMap(
            ex -> !isKnownProviderException(ex),
            ex -> {
              log.error("Error inesperado Banxico (oportuno)", ex);
              return new BanxicoException("No se pudo consultar Banxico (oportuno)", ex);
            });
  }

  private boolean isKnownProviderException(Throwable ex) {
    Throwable unwrapped = Exceptions.unwrap(ex);
    return unwrapped instanceof ExternalProviderRateLimitException
        || unwrapped instanceof ExternalProviderInvalidSymbolException
        || unwrapped instanceof ExternalProviderUpstreamException;
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record BanxicoOportunoResponse(BmxWrapper bmx) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record BmxWrapper(List<BanxicoSerie> series) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record BanxicoSerie(String idSerie, List<BanxicoDato> datos) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record BanxicoDato(String fecha, String dato) {}
}
