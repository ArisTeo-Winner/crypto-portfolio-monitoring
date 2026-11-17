package com.mx.cryptomonitor.integration.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.math.BigDecimal;
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

import com.mx.cryptomonitor.marketdata.infrastructure.outbound.twelvedata.TwelveDataMarketStateAdapter;

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

  /**
   * ADR-0033: {@code /market_state} alimenta la campana de apertura. Exige el contrato que lee
   * {@code TwelveDataMarketStateAdapter}: una lista con {@code is_market_open} y {@code
   * time_to_open} en formato {@code HH:MM:SS} para NYSE y BMV.
   */
  @Test
  void marketStateContractIsStableForNyseAndBmv() {
    String apiKey = requireApiKey();

    for (String mic : List.of("XNYS", "XMEX")) {
      Object body = get("/market_state", "code", mic, apiKey);

      assertThat(body)
          .as(
              "TwelveData /market_state no devolvio una lista para %s: plan sin acceso, cuota"
                  + " agotada o contrato cambiado. Respuesta: %s",
              mic, body)
          .isInstanceOf(List.class);
      List<?> rows = (List<?>) body;
      assertThat(rows).as("TwelveData /market_state sin filas para %s", mic).isNotEmpty();
      Map<?, ?> row = (Map<?, ?>) rows.get(0);
      assertThat(row.get("is_market_open")).isInstanceOf(Boolean.class);
      assertThat(
              TwelveDataMarketStateAdapter.parseCountdown(String.valueOf(row.get("time_to_open"))))
          .as("time_to_open de %s no tiene formato HH:MM:SS: %s", mic, row.get("time_to_open"))
          .isPresent();
    }
  }

  /**
   * ADR-0033: {@code /eod} confirma el cierre por fecha de sesion. Exige {@code datetime} con fecha
   * ISO y {@code close} numerico, que es lo que lee {@code TwelveDataEndOfDayAdapter}.
   */
  @Test
  void endOfDayContractIsStable() {
    String apiKey = requireApiKey();

    Object body = get("/eod", "symbol", KNOWN_SYMBOL, apiKey);

    assertThat(body).isInstanceOf(Map.class);
    Map<?, ?> eod = (Map<?, ?>) body;
    assertThat(eod.get("status"))
        .as(
            "TwelveData /eod respondio error para %s: plan sin acceso o cuota agotada. Detalle: %s",
            KNOWN_SYMBOL, eod.get("message"))
        .isNotEqualTo("error");
    assertThat(String.valueOf(eod.get("datetime"))).matches("\\d{4}-\\d{2}-\\d{2}.*");
    assertThat(new BigDecimal(String.valueOf(eod.get("close")))).isPositive();
  }

  private String requireApiKey() {
    String apiKey = System.getenv("TWELVEDATA_API_KEY");
    Assumptions.assumeTrue(
        StringUtils.hasText(apiKey),
        "TWELVEDATA_API_KEY no esta configurado en el entorno, se omite la validacion contra la API"
            + " real");
    return apiKey;
  }

  private Object get(String path, String paramName, String paramValue, String apiKey) {
    String baseUrl = System.getenv("API_TWELVEDATA_BASE_URL");
    if (!StringUtils.hasText(baseUrl)) {
      baseUrl = "https://api.twelvedata.com";
    }
    try {
      return WebClient.builder()
          .baseUrl(baseUrl)
          .build()
          .get()
          .uri(
              uriBuilder ->
                  uriBuilder
                      .path(path)
                      .queryParam(paramName, paramValue)
                      .queryParam("apikey", apiKey)
                      .build())
          .retrieve()
          .bodyToMono(Object.class)
          .timeout(Duration.ofSeconds(10))
          .block();
    } catch (WebClientResponseException ex) {
      // El mensaje de la excepcion incluye la URL con la API key: solo se reporta el status.
      return fail(
          "TwelveData respondio HTTP " + ex.getStatusCode().value() + " para " + path + ".");
    }
  }
}
