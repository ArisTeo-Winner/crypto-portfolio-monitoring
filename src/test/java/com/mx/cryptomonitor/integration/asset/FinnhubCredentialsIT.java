package com.mx.cryptomonitor.integration.asset;

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
 * Valida FINNHUB_API_KEY/FINNHUB_BASE_URL contra la API real de Finnhub (no un mock). Sufijo IT: no
 * corre con `mvn test`, solo con `mvn verify` — requiere red saliente y credenciales reales, por lo
 * que se omite (no falla) si no hay API key en el entorno.
 *
 * <p>A diferencia de BanxicoCredentialsIT/DataBursatilCredentialsIT, este test NO reutiliza
 * FinnhubProfileAdapter: ese adapter atrapa toda RuntimeException y devuelve Optional.empty()
 * intencionalmente (ver FinnhubProfileAdapterTest#httpErrorReturnsEmptyWithoutThrowing), por diseno
 * de resiliencia — el logo es un enriquecimiento opcional que no debe tumbar el catalogo. Eso hace
 * indistinguible, a traves del adapter, un token caducado de un simbolo inexistente. Aqui se llama
 * la API cruda para poder inspeccionar el status HTTP real (401/403 = key invalida o plan/licencia
 * caducada).
 */
class FinnhubCredentialsIT {

  private static final String KNOWN_SYMBOL = "AAPL";

  @Test
  void apiKeyIsAcceptedByFinnhub() {
    String apiKey = System.getenv("FINNHUB_API_KEY");
    Assumptions.assumeTrue(
        StringUtils.hasText(apiKey),
        "FINNHUB_API_KEY no esta configurado en el entorno, se omite la validacion contra la API"
            + " real");

    String baseUrl = System.getenv("FINNHUB_BASE_URL");
    if (!StringUtils.hasText(baseUrl)) {
      baseUrl = "https://finnhub.io/api/v1";
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
                          .path("/stock/profile2")
                          .queryParam("symbol", KNOWN_SYMBOL)
                          .queryParam("token", apiKey)
                          .build())
              .retrieve()
              .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
              .timeout(Duration.ofSeconds(10))
              .block();
    } catch (WebClientResponseException ex) {
      HttpStatusCode status = ex.getStatusCode();
      if (status.value() == 401 || status.value() == 403) {
        fail(
            "Finnhub rechazo FINNHUB_API_KEY con HTTP "
                + status.value()
                + ": key invalida, revocada o plan/licencia caducada.",
            ex);
      } else {
        fail(
            "Finnhub respondio HTTP "
                + status.value()
                + " para /stock/profile2 con una key en teoria valida. Causa: "
                + ex.getMessage(),
            ex);
      }
      return;
    } catch (RuntimeException ex) {
      fail(
          "No se pudo contactar a Finnhub (FINNHUB_BASE_URL=" + baseUrl + "): " + ex.getMessage(),
          ex);
      return;
    }

    assertThat(body)
        .as(
            "Finnhub /stock/profile2 respondio 200 sin datos para el simbolo conocido "
                + KNOWN_SYMBOL
                + " con una key en teoria valida: revisar si el contrato de la API cambio o si el"
                + " plan no incluye este endpoint")
        .isNotNull()
        .containsKey("name");
  }
}
