package com.mx.cryptomonitor.integration.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Valida el contrato en vivo de Binance USD-M Futures (indexPriceKlines) contra la API real (no un
 * mock). El endpoint es publico (no requiere token), asi que aqui no hay credencial que validar; el
 * proposito es detectar cambios de contrato o del listado antes de dar por terminada la
 * integracion: el par indice debe seguir existiendo y el arreglo debe conservar open-time en [0] y
 * precio de cierre en [4], que es lo que BinanceFuturesMarketPriceHistoryAdapter mapea. Sufijo IT:
 * no corre con `mvn test`, solo con `mvn verify` (requiere red saliente).
 */
class BinanceFuturesCredentialsIT {

  @Test
  void indexPriceKlinesContractIsStable() {
    String baseUrl = System.getenv("EXTERNAL_PROVIDERS_BINANCE_FUTURES_BASE_URL");
    if (!StringUtils.hasText(baseUrl)) {
      baseUrl = "https://fapi.binance.com";
    }

    WebClient webClient = WebClient.builder().baseUrl(baseUrl).build();

    List<List<Object>> klines;
    try {
      klines =
          webClient
              .get()
              .uri(
                  uriBuilder ->
                      uriBuilder
                          .path("/fapi/v1/indexPriceKlines")
                          .queryParam("pair", "BTCUSDT")
                          .queryParam("interval", "1d")
                          .queryParam("limit", 2)
                          .build())
              .retrieve()
              .bodyToMono(new ParameterizedTypeReference<List<List<Object>>>() {})
              .timeout(Duration.ofSeconds(10))
              .block();
    } catch (WebClientResponseException ex) {
      HttpStatusCode status = ex.getStatusCode();
      fail(
          "Binance futures respondio HTTP "
              + status.value()
              + " para indexPriceKlines?pair=BTCUSDT: revisar si el endpoint, el par o el contrato"
              + " cambiaron. Detalle: "
              + ex.getMessage(),
          ex);
      return;
    } catch (RuntimeException ex) {
      fail(
          "No se pudo contactar a Binance futures (base-url=" + baseUrl + "): " + ex.getMessage(),
          ex);
      return;
    }

    assertThat(klines)
        .as("indexPriceKlines?pair=BTCUSDT devolvio una respuesta vacia o nula")
        .isNotNull()
        .isNotEmpty();

    List<Object> firstCandle = klines.get(0);
    assertThat(firstCandle)
        .as(
            "El arreglo de indexPriceKlines cambio de forma: se esperan al menos 5 campos con"
                + " open-time en [0] y precio de cierre en [4]")
        .hasSizeGreaterThanOrEqualTo(5);

    long openTime = Long.parseLong(String.valueOf(firstCandle.get(0)));
    double closePrice = Double.parseDouble(String.valueOf(firstCandle.get(4)));
    assertThat(openTime).as("open-time [0] no es un epoch-millis valido").isPositive();
    assertThat(closePrice).as("precio de cierre [4] no es un numero positivo").isPositive();
  }
}
