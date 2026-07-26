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
 * Valida MASSIVE_API_KEY/API_MASSIVE_BASE_URL contra la API real de Massive (no un mock). Sufijo
 * IT: no corre con `mvn test`, solo con `mvn verify` — requiere red saliente y credenciales reales,
 * por lo que se omite (no falla) si faltan la key o la base URL en el entorno (Massive no tiene
 * base URL publica por defecto).
 *
 * <p>Se replica el endpoint que usa MassiveAdapter.getLatest ({@code
 * /v2/aggs/ticker/{symbol}/prev}) y su contrato: {@code {"status":"OK","results":[...]}}. Key
 * invalida → HTTP 401/403 o status != OK.
 */
class MassiveCredentialsIT {

  private static final String KNOWN_SYMBOL = "AAPL";

  @Test
  void apiKeyIsAcceptedByMassive() {
    String apiKey = System.getenv("MASSIVE_API_KEY");
    String baseUrl = System.getenv("API_MASSIVE_BASE_URL");
    Assumptions.assumeTrue(
        StringUtils.hasText(apiKey) && StringUtils.hasText(baseUrl),
        "MASSIVE_API_KEY o API_MASSIVE_BASE_URL no estan configurados en el entorno, se omite la"
            + " validacion contra la API real");

    WebClient webClient = WebClient.builder().baseUrl(baseUrl).build();

    Map<String, Object> body;
    try {
      body =
          webClient
              .get()
              .uri(
                  uriBuilder ->
                      uriBuilder
                          .path("/v2/aggs/ticker/{symbol}/prev")
                          .queryParam("adjusted", "true")
                          .queryParam("apiKey", apiKey)
                          .build(KNOWN_SYMBOL))
              .retrieve()
              .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
              .timeout(Duration.ofSeconds(10))
              .block();
    } catch (WebClientResponseException ex) {
      HttpStatusCode status = ex.getStatusCode();
      if (status.value() == 401 || status.value() == 403) {
        fail(
            "Massive rechazo MASSIVE_API_KEY con HTTP "
                + status.value()
                + ": key invalida, revocada o plan/licencia caducada.",
            ex);
      } else {
        fail(
            "Massive respondio HTTP "
                + status.value()
                + " para /v2/aggs/ticker/prev con una key en teoria valida. Causa: "
                + ex.getMessage(),
            ex);
      }
      return;
    } catch (RuntimeException ex) {
      fail(
          "No se pudo contactar a Massive (API_MASSIVE_BASE_URL="
              + baseUrl
              + "): "
              + ex.getMessage(),
          ex);
      return;
    }

    String status = body == null ? "" : String.valueOf(body.get("status")).trim().toUpperCase();
    if (!"OK".equals(status)) {
      fail(
          "Massive respondio status='"
              + status
              + "' para "
              + KNOWN_SYMBOL
              + " con una key en teoria valida: key invalida, cuota agotada o contrato cambiado."
              + " Detalle: "
              + (body == null ? "sin body" : body.getOrDefault("error", body.get("message"))));
    }

    assertThat(body)
        .as(
            "Massive /prev respondio status=OK sin el campo 'results' con una key en teoria valida:"
                + " revisar si el contrato de la API cambio")
        .isNotNull()
        .containsKey("results");
  }
}
