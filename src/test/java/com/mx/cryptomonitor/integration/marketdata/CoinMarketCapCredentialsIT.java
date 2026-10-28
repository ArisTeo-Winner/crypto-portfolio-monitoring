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
 * Valida COINMARKETCAP_API_KEY/API_COINMARKETCAP_BASE_URL contra la API real de CoinMarketCap (no
 * un mock). Sufijo IT: no corre con `mvn test`, solo con `mvn verify` — requiere red saliente y
 * credenciales reales, por lo que se omite (no falla) si no hay key en el entorno.
 *
 * <p>Se llama la API cruda (no el adapter) para inspeccionar el status HTTP real: 401/403 = key
 * invalida, revocada o plan/licencia caducada. El endpoint /v1/key/info valida la llave sin
 * consumir creditos de cotizacion.
 */
class CoinMarketCapCredentialsIT {

  @Test
  void apiKeyIsAcceptedByCoinMarketCap() {
    String apiKey = System.getenv("COINMARKETCAP_API_KEY");
    Assumptions.assumeTrue(
        StringUtils.hasText(apiKey),
        "COINMARKETCAP_API_KEY no esta configurado en el entorno, se omite la validacion contra la"
            + " API real");

    String baseUrl = System.getenv("API_COINMARKETCAP_BASE_URL");
    if (!StringUtils.hasText(baseUrl)) {
      baseUrl = "https://pro-api.coinmarketcap.com";
    }

    WebClient webClient =
        WebClient.builder().baseUrl(baseUrl).defaultHeader("X-CMC_PRO_API_KEY", apiKey).build();

    Map<String, Object> body;
    try {
      body =
          webClient
              .get()
              .uri("/v1/key/info")
              .retrieve()
              .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
              .timeout(Duration.ofSeconds(10))
              .block();
    } catch (WebClientResponseException ex) {
      HttpStatusCode status = ex.getStatusCode();
      if (status.value() == 401 || status.value() == 403) {
        fail(
            "CoinMarketCap rechazo COINMARKETCAP_API_KEY con HTTP "
                + status.value()
                + ": key invalida, revocada o plan/licencia caducada.",
            ex);
      } else {
        fail(
            "CoinMarketCap respondio HTTP "
                + status.value()
                + " para /v1/key/info con una key en teoria valida. Causa: "
                + ex.getMessage(),
            ex);
      }
      return;
    } catch (RuntimeException ex) {
      fail(
          "No se pudo contactar a CoinMarketCap (API_COINMARKETCAP_BASE_URL="
              + baseUrl
              + "): "
              + ex.getMessage(),
          ex);
      return;
    }

    assertThat(body)
        .as(
            "CoinMarketCap /v1/key/info respondio 200 sin el campo 'data' con una key en teoria"
                + " valida: revisar si el contrato de la API cambio")
        .isNotNull()
        .containsKey("data");
  }
}
