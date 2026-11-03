package com.mx.cryptomonitor.integration.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Valida en vivo el contrato de Kraken OHLC con {@code assetVersion=1} (ADR-0021/ADR-0022,
 * protocolo *CredentialsIT del CLAUDE.md). Kraken es un proveedor PÚBLICO sin API key, así que no
 * hay credencial que verificar: el test confirma que el endpoint sigue US-OK, que {@code
 * assetVersion=1} sigue devolviendo la llave en display ({@code BTC/USD}) y que la vela conserva su
 * forma ([time, open, high, low, close, ...]). Sufijo IT: requiere red saliente; no corre con
 * {@code mvn test}.
 */
class KrakenCredentialsIT {

  private static final String BASE_URL = "https://api.kraken.com";
  private static final String PATH = "/0/public/OHLC";
  private static final String LAST_KEY = "last";

  @Test
  void ohlcAssetVersionOneContractIsStable() {
    WebClient webClient =
        WebClient.builder()
            .baseUrl(BASE_URL)
            .defaultHeader("User-Agent", "crypto-portfolio-monitoring")
            .build();

    Map<String, Object> body;
    try {
      body =
          webClient
              .get()
              .uri(
                  uriBuilder ->
                      uriBuilder
                          .path(PATH)
                          .queryParam("pair", "BTCUSD")
                          .queryParam("interval", 1440)
                          .queryParam("assetVersion", 1)
                          .build())
              .retrieve()
              .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
              .timeout(Duration.ofSeconds(10))
              .block();
    } catch (WebClientResponseException ex) {
      HttpStatusCode status = ex.getStatusCode();
      if (status.value() == 451) {
        fail(
            "Kraken respondio HTTP 451 (geobloqueo) desde esta IP: se esperaba que Kraken fuera"
                + " US-OK (ADR-0021). Detalle: "
                + ex.getMessage(),
            ex);
      } else {
        fail(
            "Kraken respondio HTTP "
                + status.value()
                + " para "
                + PATH
                + ": revisar si el endpoint o el contrato cambiaron. Detalle: "
                + ex.getMessage(),
            ex);
      }
      return;
    } catch (RuntimeException ex) {
      fail("No se pudo contactar a Kraken (" + BASE_URL + "): " + ex.getMessage(), ex);
      return;
    }

    assertThat(body).as("Respuesta nula de Kraken").isNotNull();

    @SuppressWarnings("unchecked")
    List<String> errors = (List<String>) body.get("error");
    assertThat(errors).as("Kraken devolvio error de negocio: " + errors).isNullOrEmpty();

    @SuppressWarnings("unchecked")
    Map<String, Object> result = (Map<String, Object>) body.get("result");
    assertThat(result).as("Respuesta sin 'result'").isNotNull().containsKey(LAST_KEY);

    String pairKey =
        result.keySet().stream()
            .filter(key -> !LAST_KEY.equals(key))
            .findFirst()
            .orElseThrow(
                () -> new AssertionError("Kraken no devolvio una llave de par en 'result'"));
    assertThat(pairKey)
        .as("assetVersion=1 debe devolver la llave en display (p. ej. BTC/USD)")
        .isEqualTo("BTC/USD");

    @SuppressWarnings("unchecked")
    List<List<Object>> candles = (List<List<Object>>) result.get(pairKey);
    assertThat(candles).as("Kraken no devolvio velas").isNotNull().isNotEmpty();

    List<Object> firstCandle = candles.get(0);
    assertThat(firstCandle)
        .as("La vela de Kraken cambio de forma: se esperan al menos 5 campos (OHLC)")
        .hasSizeGreaterThanOrEqualTo(5);

    long time = Long.parseLong(String.valueOf(firstCandle.get(0)));
    double close = Double.parseDouble(String.valueOf(firstCandle.get(4)));
    assertThat(time).as("time no es un epoch-seconds valido").isPositive();
    assertThat(close).as("precio de cierre (indice 4) no es un numero positivo").isPositive();
  }
}
