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
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Valida FMP_API_KEY contra la API real de FMP en el endpoint que consume {@code
 * FmpDividendAdapter} ({@code GET /stable/dividends?symbol=}). Sufijo IT: requiere red + key real;
 * se omite (no falla) si falta {@code FMP_API_KEY}. No se incluye {@code ex.getMessage()} en los
 * fallos: la URL lleva el {@code apikey} como query param y no debe quedar en surefire-reports
 * (CLAUDE.md: secretos nunca en logs). Key invalida o plan sin dividendos -> HTTP 401/403.
 */
class FmpDividendsCredentialsIT {

  @Test
  @SuppressWarnings("unchecked")
  void dividendsEndpointMatchesContract() {
    String apiKey = System.getenv("FMP_API_KEY");
    Assumptions.assumeTrue(
        StringUtils.hasText(apiKey),
        "FMP_API_KEY no esta configurado en el entorno, se omite la validacion contra la API real");

    String baseUrl = System.getenv("FMP_BASE_URL");
    if (!StringUtils.hasText(baseUrl)) {
      baseUrl = "https://financialmodelingprep.com";
    }

    WebClient webClient = WebClient.builder().baseUrl(baseUrl).build();

    List<Map<String, Object>> data = null;
    try {
      data =
          webClient
              .get()
              .uri(
                  uriBuilder ->
                      uriBuilder
                          .path("/stable/dividends")
                          .queryParam("symbol", "GOOGL")
                          .queryParam("apikey", apiKey)
                          .build())
              .retrieve()
              .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
              .timeout(Duration.ofSeconds(10))
              .block();
    } catch (WebClientResponseException ex) {
      HttpStatusCode status = ex.getStatusCode();
      if (status.value() == 401 || status.value() == 403) {
        fail(
            "FMP rechazo FMP_API_KEY con HTTP "
                + status.value()
                + " para /stable/dividends: key invalida/revocada o el plan no cubre dividendos"
                + " (gate del FmpDividendAdapter).");
      }
      fail("FMP respondio HTTP " + status.value() + " para /stable/dividends con una key valida.");
      return;
    } catch (RuntimeException ex) {
      fail("No se pudo contactar a FMP /stable/dividends: " + ex.getClass().getSimpleName());
      return;
    }

    assertThat(data)
        .as("FMP /stable/dividends para GOOGL devolvio vacio con una key en teoria valida")
        .isNotEmpty();
    assertThat(data)
        .as(
            "FMP /stable/dividends: ninguna fila con el contrato date/paymentDate/dividend que consume"
                + " el FmpDividendAdapter")
        .anySatisfy(
            row -> {
              assertThat(row.get("date")).isNotNull();
              assertThat(row.get("paymentDate")).isNotNull();
              assertThat(row.get("dividend")).isNotNull();
            });
  }
}
