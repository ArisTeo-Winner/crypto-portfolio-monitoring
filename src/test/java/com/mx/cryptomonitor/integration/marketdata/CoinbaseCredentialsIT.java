package com.mx.cryptomonitor.integration.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.mx.cryptomonitor.portfolio.infrastructure.outbound.marketdata.CoinbaseJwtSigner;

/**
 * Valida en vivo la credencial CDP de Coinbase y el contrato de Advanced Trade ({@code GET
 * /api/v3/brokerage/products/BTC-USD/candles}) contra la API real (ADR-0021/ADR-0023,
 * [[ADR-0002]]). A diferencia del endpoint público keyless anterior, este requiere un JWT ES256
 * firmado con la llave CDP: el test ejercita el {@link CoinbaseJwtSigner} real (parseo de PEM SEC1
 * + firma) y confirma token vigente, licencia activa y forma del JSON. Se omite (no falla) si no
 * hay credencial en el entorno. Sufijo IT: requiere red saliente; no corre con `mvn test`.
 */
class CoinbaseCredentialsIT {

  private static final String BASE_URL = "https://api.coinbase.com";
  private static final String PATH = "/api/v3/brokerage/products/BTC-USD/candles";

  @Test
  void authenticatedDailyCandlesContractIsStable() {
    String keyName = System.getenv("COINBASE_CDP_KEY_NAME");
    String privateKeyPem = System.getenv("COINBASE_CDP_PRIVATE_KEY");
    assumeTrue(
        StringUtils.hasText(keyName) && StringUtils.hasText(privateKeyPem),
        "Sin credencial CDP (COINBASE_CDP_KEY_NAME / COINBASE_CDP_PRIVATE_KEY): se omite el"
            + " health-check en vivo de Coinbase.");

    CoinbaseJwtSigner signer = new CoinbaseJwtSigner(keyName, privateKeyPem, Clock.systemUTC());
    assertThat(signer.isConfigured())
        .as("La llave privada CDP no se pudo parsear (revisar formato PEM SEC1)")
        .isTrue();

    String bearer = signer.sign("GET", PATH);
    long end = Instant.now().getEpochSecond();
    long start = Instant.now().minus(Duration.ofDays(3)).getEpochSecond();

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
                          .queryParam("start", start)
                          .queryParam("end", end)
                          .queryParam("granularity", "ONE_DAY")
                          .build())
              .header("Authorization", "Bearer " + bearer)
              .retrieve()
              .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
              .timeout(Duration.ofSeconds(10))
              .block();
    } catch (WebClientResponseException ex) {
      HttpStatusCode status = ex.getStatusCode();
      if (status.value() == 401 || status.value() == 403) {
        fail(
            "Coinbase respondio HTTP "
                + status.value()
                + " (auth): la credencial CDP esta vencida, revocada o mal configurada. Detalle: "
                + ex.getMessage(),
            ex);
      } else if (status.value() == 451) {
        fail(
            "Coinbase respondio HTTP 451 (geobloqueo) desde esta IP: revisar region del host; se"
                + " esperaba que Coinbase fuera US-OK (ADR-0021). Detalle: "
                + ex.getMessage(),
            ex);
      } else {
        fail(
            "Coinbase respondio HTTP "
                + status.value()
                + " para "
                + PATH
                + ": revisar si el endpoint o el contrato cambiaron. Detalle: "
                + ex.getMessage(),
            ex);
      }
      return;
    } catch (RuntimeException ex) {
      fail("No se pudo contactar a Coinbase (" + BASE_URL + "): " + ex.getMessage(), ex);
      return;
    }

    assertThat(body).as("Respuesta nula de Coinbase").isNotNull().containsKey("candles");

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> candles = (List<Map<String, Object>>) body.get("candles");
    assertThat(candles).as("Coinbase no devolvio velas").isNotNull().isNotEmpty();

    Map<String, Object> firstCandle = candles.get(0);
    assertThat(firstCandle)
        .as("La vela de Coinbase cambio de forma: se esperan campos start y close")
        .containsKeys("start", "close");

    long startTime = Long.parseLong(String.valueOf(firstCandle.get("start")));
    double closePrice = Double.parseDouble(String.valueOf(firstCandle.get("close")));
    assertThat(startTime).as("start no es un epoch-seconds valido").isPositive();
    assertThat(closePrice).as("precio de cierre no es un numero positivo").isPositive();
  }
}
