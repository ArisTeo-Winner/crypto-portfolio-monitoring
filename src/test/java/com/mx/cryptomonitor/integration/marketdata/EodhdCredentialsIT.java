package com.mx.cryptomonitor.integration.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Valida EODHD_API_TOKEN contra la API real de EODHD ({@code GET /api/real-time/{symbol}}),
 * ADR-0016 / ADR-0002. Sufijo IT: requiere red + token real; se omite (no falla) si falta
 * EODHD_API_TOKEN. Replica el request de {@code EodhdAdapter.getLatest} (AAPL.US) y su contrato:
 * objeto JSON con {@code close}. Token invalido -> HTTP 401/403.
 */
class EodhdCredentialsIT {

  private static final String BASE_URL_DEFAULT = "https://eodhd.com";

  @Test
  void apiTokenIsAcceptedByEodhd() {
    String apiToken = System.getenv("EODHD_API_TOKEN");
    Assumptions.assumeTrue(
        StringUtils.hasText(apiToken),
        "EODHD_API_TOKEN no esta configurado en el entorno, se omite la validacion contra la API"
            + " real");
    String baseUrl = System.getenv().getOrDefault("EODHD_BASE_URL", BASE_URL_DEFAULT);

    WebClient webClient = WebClient.builder().baseUrl(baseUrl).build();

    Map<String, Object> body;
    try {
      body =
          webClient
              .get()
              .uri(
                  uriBuilder ->
                      uriBuilder
                          .path("/api/real-time/AAPL.US")
                          .queryParam("api_token", apiToken)
                          .queryParam("fmt", "json")
                          .build())
              .retrieve()
              .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
              .timeout(Duration.ofSeconds(10))
              .block();
    } catch (WebClientResponseException ex) {
      HttpStatusCode status = ex.getStatusCode();
      if (status.value() == 401 || status.value() == 403) {
        fail(
            "EODHD rechazo EODHD_API_TOKEN con HTTP "
                + status.value()
                + ": token invalido, revocado o plan/licencia caducada.",
            ex);
      } else {
        fail(
            "EODHD respondio HTTP "
                + status.value()
                + " para /api/real-time/AAPL.US con un token en teoria valido. Causa: "
                + ex.getMessage(),
            ex);
      }
      return;
    } catch (RuntimeException ex) {
      fail("No se pudo contactar a EODHD (" + baseUrl + "): " + ex.getMessage(), ex);
      return;
    }

    assertThat(body)
        .as("EODHD /api/real-time/AAPL.US no devolvio body con un token en teoria valido")
        .isNotNull();
    assertThat(body.get("close"))
        .as(
            "EODHD /api/real-time/AAPL.US respondio sin 'close' con un token en teoria valido:"
                + " token invalido, cuota agotada o contrato cambiado. Detalle: "
                + body)
        .isNotNull();
  }
}
