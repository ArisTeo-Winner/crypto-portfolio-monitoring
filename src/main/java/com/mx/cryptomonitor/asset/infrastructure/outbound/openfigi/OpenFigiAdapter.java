package com.mx.cryptomonitor.asset.infrastructure.outbound.openfigi;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.mx.cryptomonitor.asset.application.port.out.InstrumentIdentityPort;

import lombok.extern.slf4j.Slf4j;

/**
 * Adaptador OpenFIGI {@code POST /v3/mapping} (ADR-0019): traduce TICKER (+ exchCode) a FIGI
 * canonico (compositeFIGI / shareClassFIGI) y metadata estatica. Solo identidad; sin precios ni
 * corporate actions. La API key viaja en el header {@code X-OPENFIGI-APIKEY} (opcional: el endpoint
 * publico responde sin ella con rate-limit mas estricto); nunca se loggea.
 */
@Component
@Slf4j
public class OpenFigiAdapter implements InstrumentIdentityPort {

  private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

  private final WebClient webClient;
  private final String apiKey;

  public OpenFigiAdapter(
      @Qualifier("openFigiWebClient") WebClient webClient,
      @Value("${openfigi.api-key:}") String apiKey) {
    this.webClient = webClient;
    this.apiKey = apiKey;
  }

  @Override
  public Optional<InstrumentIdentity> resolveByTicker(String ticker, String exchCode) {
    if (ticker == null || ticker.isBlank()) {
      return Optional.empty();
    }
    String normalizedTicker = ticker.trim().toUpperCase(Locale.ROOT);
    String normalizedExch =
        (exchCode == null || exchCode.isBlank()) ? null : exchCode.trim().toUpperCase(Locale.ROOT);
    MappingQuery query = new MappingQuery("TICKER", normalizedTicker, normalizedExch);

    try {
      WebClient.RequestBodySpec spec =
          webClient.post().uri("/v3/mapping").contentType(MediaType.APPLICATION_JSON);
      if (apiKey != null && !apiKey.isBlank()) {
        spec = spec.header("X-OPENFIGI-APIKEY", apiKey);
      }
      List<MappingResult> results =
          spec.bodyValue(List.of(query))
              .retrieve()
              .bodyToMono(new ParameterizedTypeReference<List<MappingResult>>() {})
              .timeout(REQUEST_TIMEOUT)
              .block();
      return toIdentity(results);
    } catch (WebClientResponseException ex) {
      log.warn(
          "OpenFIGI mapping fallo para {} (exch={}): HTTP {}",
          normalizedTicker,
          normalizedExch,
          ex.getStatusCode());
      return Optional.empty();
    } catch (RuntimeException ex) {
      log.warn(
          "OpenFIGI mapping fallo para {} (exch={}): {}",
          normalizedTicker,
          normalizedExch,
          ex.getClass().getSimpleName());
      return Optional.empty();
    }
  }

  private Optional<InstrumentIdentity> toIdentity(List<MappingResult> results) {
    if (results == null || results.isEmpty()) {
      return Optional.empty();
    }
    MappingResult first = results.get(0);
    if (first == null || first.data() == null || first.data().isEmpty()) {
      return Optional.empty();
    }
    FigiRecord record = first.data().get(0);
    if (record == null || (isBlank(record.shareClassFigi()) && isBlank(record.compositeFigi()))) {
      return Optional.empty();
    }
    return Optional.of(
        new InstrumentIdentity(
            record.compositeFigi(),
            record.shareClassFigi(),
            record.securityType(),
            record.marketSector(),
            record.name()));
  }

  private static boolean isBlank(String s) {
    return s == null || s.isBlank();
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private record MappingQuery(String idType, String idValue, String exchCode) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record MappingResult(List<FigiRecord> data, String warning, String error) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record FigiRecord(
      String figi,
      String name,
      String ticker,
      String exchCode,
      @JsonProperty("compositeFIGI") String compositeFigi,
      @JsonProperty("shareClassFIGI") String shareClassFigi,
      String securityType,
      String marketSector) {}
}
