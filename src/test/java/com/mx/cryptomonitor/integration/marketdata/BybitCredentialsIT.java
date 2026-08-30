package com.mx.cryptomonitor.integration.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Valida el contrato en vivo de Bybit v5 (spot kline) contra la API real (no un mock). El endpoint
 * es publico (no requiere token), asi que aqui no hay credencial que validar; el proposito es
 * detectar cambios de contrato o del listado antes de dar por terminada la integracion: el par spot
 * debe seguir existiendo (retCode 0) y cada vela debe conservar open-time en [0] y precio de cierre
 * en [4], que es lo que BybitMarketPriceHistoryAdapter mapea. Sufijo IT: no corre con `mvn test`,
 * solo con `mvn verify` (requiere red saliente).
 */
class BybitCredentialsIT {

  @Test
  @SuppressWarnings("unchecked")
  void spotKlineContractIsStable() {
    String baseUrl = System.getenv("EXTERNAL_PROVIDERS_BYBIT_BASE_URL");
    if (!StringUtils.hasText(baseUrl)) {
      baseUrl = "https://api.bybit.com";
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
                          .path("/v5/market/kline")
                          .queryParam("category", "spot")
                          .queryParam("symbol", "BTCUSDT")
                          .queryParam("interval", "D")
                          .queryParam("limit", 2)
                          .build())
              .retrieve()
              .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
              .timeout(Duration.ofSeconds(10))
              .block();
    } catch (WebClientResponseException ex) {
      HttpStatusCode status = ex.getStatusCode();
      fail(
          "Bybit respondio HTTP "
              + status.value()
              + " para /v5/market/kline?symbol=BTCUSDT: revisar si el endpoint o el contrato"
              + " cambiaron. Detalle: "
              + ex.getMessage(),
          ex);
      return;
    } catch (RuntimeException ex) {
      fail("No se pudo contactar a Bybit (base-url=" + baseUrl + "): " + ex.getMessage(), ex);
      return;
    }

    assertThat(body).as("Bybit devolvio respuesta nula").isNotNull();
    assertThat(body.get("retCode"))
        .as("Bybit retCode != 0 para BTCUSDT spot: par no soportado o error de contrato")
        .isEqualTo(0);

    Map<String, Object> result = (Map<String, Object>) body.get("result");
    assertThat(result).as("Falta 'result' en la respuesta de Bybit").isNotNull();
    List<List<Object>> list = (List<List<Object>>) result.get("list");
    assertThat(list).as("Falta 'result.list' o vino vacio").isNotNull().isNotEmpty();

    List<Object> firstCandle = list.get(0);
    assertThat(firstCandle)
        .as(
            "La vela de Bybit cambio de forma: se esperan >=5 campos con open-time [0] y cierre [4]")
        .hasSizeGreaterThanOrEqualTo(5);

    long openTime = Long.parseLong(String.valueOf(firstCandle.get(0)));
    double closePrice = Double.parseDouble(String.valueOf(firstCandle.get(4)));
    assertThat(openTime).as("open-time [0] no es un epoch-millis valido").isPositive();
    assertThat(closePrice).as("precio de cierre [4] no es un numero positivo").isPositive();
  }
}
