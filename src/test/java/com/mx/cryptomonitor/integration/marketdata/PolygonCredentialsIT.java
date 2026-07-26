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
 * Valida POLYGON_API_KEY/POLYGON_BASE_URL contra la API real de Polygon (no un mock). Sufijo IT: no
 * corre con `mvn test`, solo con `mvn verify` — requiere red saliente y credenciales reales, por lo
 * que se omite (no falla) si no hay key en el entorno.
 *
 * <p>El endpoint /v1/marketstatus/now valida la llave sin consumir creditos de agregados. Key
 * invalida → HTTP 401/403. Se exige el campo de contrato {@code "market"}.
 */
class PolygonCredentialsIT {

  @Test
  void apiKeyIsAcceptedByPolygon() {
    String apiKey = System.getenv("POLYGON_API_KEY");
    Assumptions.assumeTrue(
        StringUtils.hasText(apiKey),
        "POLYGON_API_KEY no esta configurado en el entorno, se omite la validacion contra la API"
            + " real");

    String baseUrl = System.getenv("POLYGON_BASE_URL");
    if (!StringUtils.hasText(baseUrl)) {
      baseUrl = "https://api.polygon.io";
    }

    WebClient webClient = WebClient.builder().baseUrl(baseUrl).build();

    Map<String, Object> body;
    try {
      body =
          webClient
              .get()
              .uri(
                  uriBuilder ->
                      uriBuilder.path("/v1/marketstatus/now").queryParam("apiKey", apiKey).build())
              .retrieve()
              .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
              .timeout(Duration.ofSeconds(10))
              .block();
    } catch (WebClientResponseException ex) {
      HttpStatusCode status = ex.getStatusCode();
      if (status.value() == 401 || status.value() == 403) {
        fail(
            "Polygon rechazo POLYGON_API_KEY con HTTP "
                + status.value()
                + ": key invalida, revocada o plan/licencia caducada.",
            ex);
      } else {
        fail(
            "Polygon respondio HTTP "
                + status.value()
                + " para /v1/marketstatus/now con una key en teoria valida. Causa: "
                + ex.getMessage(),
            ex);
      }
      return;
    } catch (RuntimeException ex) {
      fail(
          "No se pudo contactar a Polygon (POLYGON_BASE_URL=" + baseUrl + "): " + ex.getMessage(),
          ex);
      return;
    }

    assertThat(body)
        .as(
            "Polygon /v1/marketstatus/now respondio 200 sin el campo 'market' con una key en teoria"
                + " valida: revisar si el contrato de la API cambio")
        .isNotNull()
        .containsKey("market");
  }
}
