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
 * Valida TWELVEDATA_API_KEY/API_TWELVEDATA_BASE_URL contra la API real de Twelve Data (no un mock).
 * Sufijo IT: no corre con `mvn test`, solo con `mvn verify` — requiere red saliente y credenciales
 * reales, por lo que se omite (no falla) si no hay key en el entorno.
 *
 * <p>Twelve Data senaliza key invalida/expirada de dos formas: HTTP 401/403 o HTTP 200 con body
 * {@code {"status":"error","code":401}}. Este test cubre ambas, como TwelveDataAdapter, y exige el
 * campo de contrato {@code "price"}.
 */
class TwelveDataCredentialsIT {

  private static final String KNOWN_SYMBOL = "AAPL";

  @Test
  void apiKeyIsAcceptedByTwelveData() {
    String apiKey = System.getenv("TWELVEDATA_API_KEY");
    Assumptions.assumeTrue(
        StringUtils.hasText(apiKey),
        "TWELVEDATA_API_KEY no esta configurado en el entorno, se omite la validacion contra la API"
            + " real");

    String baseUrl = System.getenv("API_TWELVEDATA_BASE_URL");
    if (!StringUtils.hasText(baseUrl)) {
      baseUrl = "https://api.twelvedata.com";
    }

    WebClient webClient = WebClient.builder().baseUrl(baseUrl).build();

    Map<String, Object> body;
    try {
      body =
          webClient
              .get()
              .uri(
                  uriBuilder ->
                      uriBuilder
                          .path("/price")
                          .queryParam("symbol", KNOWN_SYMBOL)
                          .queryParam("apikey", apiKey)
                          .build())
              .retrieve()
              .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
              .timeout(Duration.ofSeconds(10))
              .block();
    } catch (WebClientResponseException ex) {
      HttpStatusCode status = ex.getStatusCode();
      if (status.value() == 401 || status.value() == 403) {
        fail(
            "TwelveData rechazo TWELVEDATA_API_KEY con HTTP "
                + status.value()
                + ": key invalida, revocada o plan/licencia caducada.",
            ex);
      } else {
        fail(
            "TwelveData respondio HTTP "
                + status.value()
                + " para /price con una key en teoria valida. Causa: "
                + ex.getMessage(),
            ex);
      }
      return;
    } catch (RuntimeException ex) {
      fail(
          "No se pudo contactar a TwelveData (API_TWELVEDATA_BASE_URL="
              + baseUrl
              + "): "
              + ex.getMessage(),
          ex);
      return;
    }

    if (body != null && "error".equalsIgnoreCase(String.valueOf(body.get("status")))) {
      Object code = body.get("code");
      Object message = body.get("message");
      if ("401".equals(String.valueOf(code)) || "403".equals(String.valueOf(code))) {
        fail(
            "TwelveData rechazo TWELVEDATA_API_KEY (HTTP 200, body code "
                + code
                + "): key invalida o expirada. Detalle: "
                + message);
      }
      fail(
          "TwelveData respondio status=error (code "
              + code
              + ") para "
              + KNOWN_SYMBOL
              + " con una key en teoria valida. Detalle: "
              + message);
    }

    assertThat(body)
        .as(
            "TwelveData /price respondio 200 sin el campo 'price' para el simbolo conocido "
                + KNOWN_SYMBOL
                + " con una key en teoria valida: revisar si el contrato de la API cambio")
        .isNotNull()
        .containsKey("price");
  }
}
