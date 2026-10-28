package com.mx.cryptomonitor.integration.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Valida OPENFIGI_API_KEY contra la API real de OpenFIGI (POST /v3/mapping), ADR-0019 / ADR-0002.
 * Sufijo IT: no corre con {@code mvn test}; requiere red saliente y una key real, por lo que se
 * omite (no falla) si falta OPENFIGI_API_KEY en el entorno.
 *
 * <p>Replica el request que usa {@code OpenFigiAdapter.resolveByTicker} ({@code
 * TICKER:IBM,exch:US}) y su contrato: array -> {@code [{"data":[{"shareClassFIGI":...}]}]}. Key
 * invalida -> HTTP 401/403; contrato cambiado -> sin {@code data}/{@code shareClassFIGI}.
 */
class OpenFigiCredentialsIT {

  private static final String BASE_URL_DEFAULT = "https://api.openfigi.com";

  @Test
  void apiKeyIsAcceptedByOpenFigi() {
    String apiKey = System.getenv("OPENFIGI_API_KEY");
    Assumptions.assumeTrue(
        StringUtils.hasText(apiKey),
        "OPENFIGI_API_KEY no esta configurada en el entorno, se omite la validacion contra la API"
            + " real");
    String baseUrl = System.getenv().getOrDefault("OPENFIGI_BASE_URL", BASE_URL_DEFAULT);

    WebClient webClient = WebClient.builder().baseUrl(baseUrl).build();

    List<Map<String, Object>> body;
    try {
      body =
          webClient
              .post()
              .uri("/v3/mapping")
              .contentType(MediaType.APPLICATION_JSON)
              .header("X-OPENFIGI-APIKEY", apiKey)
              .bodyValue(List.of(Map.of("idType", "TICKER", "idValue", "IBM", "exchCode", "US")))
              .retrieve()
              .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
              .timeout(Duration.ofSeconds(10))
              .block();
    } catch (WebClientResponseException ex) {
      HttpStatusCode status = ex.getStatusCode();
      if (status.value() == 401 || status.value() == 403) {
        fail(
            "OpenFIGI rechazo OPENFIGI_API_KEY con HTTP "
                + status.value()
                + ": key invalida o revocada.",
            ex);
      } else {
        fail(
            "OpenFIGI respondio HTTP "
                + status.value()
                + " para /v3/mapping con una key en teoria valida. Causa: "
                + ex.getMessage(),
            ex);
      }
      return;
    } catch (RuntimeException ex) {
      fail("No se pudo contactar a OpenFIGI (" + baseUrl + "): " + ex.getMessage(), ex);
      return;
    }

    assertThat(body)
        .as("OpenFIGI /v3/mapping no devolvio resultados para IBM/US con una key en teoria valida")
        .isNotNull()
        .isNotEmpty();

    Object data = body.get(0).get("data");
    assertThat(data)
        .as(
            "OpenFIGI /v3/mapping respondio sin 'data' para IBM/US: key invalida, cuota agotada o"
                + " contrato cambiado. Detalle: "
                + body.get(0))
        .isInstanceOf(List.class);
    assertThat((List<?>) data)
        .as("OpenFIGI /v3/mapping devolvio 'data' vacio para IBM/US")
        .isNotEmpty();

    Object shareClassFigi = ((Map<?, ?>) ((List<?>) data).get(0)).get("shareClassFIGI");
    assertThat(shareClassFigi)
        .as("OpenFIGI /v3/mapping no incluyo shareClassFIGI (contrato cambiado?)")
        .isNotNull();
  }
}
