package com.mx.cryptomonitor.integration.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Valida ALPHAVANTAGE_API_KEY/API_ALPHAVANTAGE_BASE_URL contra la API real de AlphaVantage (no un
 * mock). Sufijo IT: no corre con `mvn test`, solo con `mvn verify` — requiere red saliente y
 * credenciales reales, por lo que se omite (no falla) si no hay key en el entorno.
 *
 * <p>AlphaVantage no devuelve 401: ante key invalida o cuota agotada responde HTTP 200 con un
 * envelope de error {@code {"Information": ...}} o {@code {"Note": ...}}. Este test replica la
 * deteccion de AlphaVantageAdapter.throwIfProviderReportedError y ademas exige el campo de contrato
 * {@code "Global Quote"}.
 */
class AlphaVantageCredentialsIT {

  private static final String KNOWN_SYMBOL = "IBM";

  @Test
  void apiKeyIsAcceptedByAlphaVantage() {
    String apiKey = System.getenv("ALPHAVANTAGE_API_KEY");
    Assumptions.assumeTrue(
        StringUtils.hasText(apiKey),
        "ALPHAVANTAGE_API_KEY no esta configurado en el entorno, se omite la validacion contra la"
            + " API real");

    String baseUrl = System.getenv("API_ALPHAVANTAGE_BASE_URL");
    if (!StringUtils.hasText(baseUrl)) {
      baseUrl = "https://www.alphavantage.co";
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
                          .path("/query")
                          .queryParam("function", "GLOBAL_QUOTE")
                          .queryParam("symbol", KNOWN_SYMBOL)
                          .queryParam("apikey", apiKey)
                          .build())
              .retrieve()
              .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
              .timeout(Duration.ofSeconds(10))
              .block();
    } catch (RuntimeException ex) {
      fail(
          "No se pudo contactar a AlphaVantage (API_ALPHAVANTAGE_BASE_URL="
              + baseUrl
              + "): "
              + ex.getMessage(),
          ex);
      return;
    }

    if (body != null && (body.containsKey("Information") || body.containsKey("Note"))) {
      Object detail = body.getOrDefault("Information", body.get("Note"));
      fail(
          "AlphaVantage rechazo ALPHAVANTAGE_API_KEY (HTTP 200 con envelope de error): key invalida"
              + " o cuota diaria agotada. Detalle: "
              + detail);
    }
    if (body != null && body.containsKey("Error Message")) {
      fail(
          "AlphaVantage respondio 'Error Message' para "
              + KNOWN_SYMBOL
              + " con una key en teoria valida: revisar simbolo o contrato. Detalle: "
              + body.get("Error Message"));
    }

    assertThat(body)
        .as(
            "AlphaVantage /query GLOBAL_QUOTE respondio 200 sin 'Global Quote' para el simbolo"
                + " conocido "
                + KNOWN_SYMBOL
                + " con una key en teoria valida: revisar si el contrato de la API cambio")
        .isNotNull()
        .containsKey("Global Quote");
  }
}
