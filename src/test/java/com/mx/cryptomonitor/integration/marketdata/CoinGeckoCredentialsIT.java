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
 * Valida COINGECKO_API_KEY/API_COINGECKO_BASE_URL contra la API real de CoinGecko (no un mock).
 * Sufijo IT: no corre con `mvn test`, solo con `mvn verify` — requiere red saliente. La key de
 * CoinGecko es opcional (free tier); este test se omite si no hay key, y valida la key configurada
 * mas el contrato de /ping cuando si existe. El header es `x-cg-pro-api-key`, igual que en
 * CoinGeckoWebClientConfig.
 */
class CoinGeckoCredentialsIT {

  @Test
  void apiKeyIsAcceptedByCoinGecko() {
    String apiKey = System.getenv("COINGECKO_API_KEY");
    Assumptions.assumeTrue(
        StringUtils.hasText(apiKey),
        "COINGECKO_API_KEY no esta configurado en el entorno (free tier), se omite la validacion"
            + " contra la API real");

    String baseUrl = System.getenv("API_COINGECKO_BASE_URL");
    if (!StringUtils.hasText(baseUrl)) {
      baseUrl = "https://api.coingecko.com/api/v3";
    }

    WebClient webClient =
        WebClient.builder().baseUrl(baseUrl).defaultHeader("x-cg-pro-api-key", apiKey).build();

    Map<String, Object> body;
    try {
      body =
          webClient
              .get()
              .uri("/ping")
              .retrieve()
              .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
              .timeout(Duration.ofSeconds(10))
              .block();
    } catch (WebClientResponseException ex) {
      HttpStatusCode status = ex.getStatusCode();
      if (status.value() == 401 || status.value() == 403) {
        fail(
            "CoinGecko rechazo COINGECKO_API_KEY con HTTP "
                + status.value()
                + ": key invalida, revocada o plan/licencia caducada.",
            ex);
      } else {
        fail(
            "CoinGecko respondio HTTP "
                + status.value()
                + " para /ping con una key en teoria valida. Causa: "
                + ex.getMessage(),
            ex);
      }
      return;
    } catch (RuntimeException ex) {
      fail(
          "No se pudo contactar a CoinGecko (API_COINGECKO_BASE_URL="
              + baseUrl
              + "): "
              + ex.getMessage(),
          ex);
      return;
    }

    assertThat(body)
        .as(
            "CoinGecko /ping respondio 200 sin el campo 'gecko_says' con una key en teoria valida:"
                + " revisar si el contrato de la API cambio")
        .isNotNull()
        .containsKey("gecko_says");
  }

  /**
   * Valida el contrato del endpoint del que depende el logo de CRYPTO: {@code
   * /coins/markets?vs_currency=usd&symbols=btc} debe devolver el campo {@code image} (URL del
   * logo). Plan Demo: header {@code x-cg-demo-api-key}. El endpoint es publico (la key es opcional,
   * solo sube el limite de tasa), asi que valida el contrato con o sin key, y falla si la key es
   * rechazada (401/403).
   */
  @Test
  void coinsMarketsReturnsImageForBtcOnDemoPlan() {
    String baseUrl = System.getenv("API_COINGECKO_BASE_URL");
    if (!StringUtils.hasText(baseUrl)) {
      baseUrl = "https://api.coingecko.com/api/v3";
    }
    String apiKey = System.getenv("COINGECKO_API_KEY");

    WebClient.Builder builder = WebClient.builder().baseUrl(baseUrl);
    if (StringUtils.hasText(apiKey)) {
      builder = builder.defaultHeader("x-cg-demo-api-key", apiKey);
    }
    WebClient webClient = builder.build();

    List<Map<String, Object>> body;
    try {
      body =
          webClient
              .get()
              .uri(
                  uriBuilder ->
                      uriBuilder
                          .path("/coins/markets")
                          .queryParam("vs_currency", "usd")
                          .queryParam("symbols", "btc")
                          .build())
              .retrieve()
              .bodyToMono(new ParameterizedTypeReference<List<Map<String, Object>>>() {})
              .timeout(Duration.ofSeconds(10))
              .block();
    } catch (WebClientResponseException ex) {
      HttpStatusCode status = ex.getStatusCode();
      if (status.value() == 401 || status.value() == 403) {
        fail(
            "CoinGecko rechazo COINGECKO_API_KEY (plan Demo) con HTTP "
                + status.value()
                + ": key invalida/revocada o header incorrecto (se usa x-cg-demo-api-key).",
            ex);
      } else {
        fail(
            "CoinGecko /coins/markets respondio HTTP " + status.value() + ": " + ex.getMessage(),
            ex);
      }
      return;
    } catch (RuntimeException ex) {
      fail(
          "No se pudo contactar a CoinGecko /coins/markets (base="
              + baseUrl
              + "): "
              + ex.getMessage(),
          ex);
      return;
    }

    assertThat(body)
        .as("CoinGecko /coins/markets?symbols=btc debe devolver al menos un resultado")
        .isNotNull()
        .isNotEmpty();
    assertThat(body.get(0).get("image"))
        .as(
            "El contrato de /coins/markets debe incluir 'image' (URL del logo): de ese campo depende"
                + " el logo de CRYPTO en el catalogo")
        .isInstanceOf(String.class);
    assertThat((String) body.get(0).get("image")).isNotBlank();
  }
}
