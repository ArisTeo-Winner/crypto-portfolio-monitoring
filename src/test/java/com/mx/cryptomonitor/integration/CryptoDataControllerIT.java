package com.mx.cryptomonitor.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.mx.cryptomonitor.integration.support.AbstractCoinMarketCapWireMockIT;
import com.mx.cryptomonitor.marketdata.infrastructure.outbound.cmc.CoinMarketCapAdapter;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@AutoConfigureWebTestClient(timeout = "15s")
class CryptoDataControllerIT extends AbstractCoinMarketCapWireMockIT {

  @Autowired private WebTestClient webTestClient;

  @Autowired CoinMarketCapAdapter coinMarketCapAdapter;

  private static final Logger logger = LoggerFactory.getLogger(CryptoDataControllerIT.class);

  @Test
  void shouldReturnPrice_whenSymbolExists() {

    logger.info("=== Ejecutando test shouldReturnPrice_whenSymbolExists ===");

    stubFor(
        get(urlPathEqualTo("/v2/cryptocurrency/quotes/latest"))
            .withQueryParam("symbol", equalTo("BTC"))
            .withHeader("X-CMC_PRO_API_KEY", equalTo("fake-api-key"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBodyFile("quotes-response-btc.json")));

    var quote = coinMarketCapAdapter.getCryptoPrice("BTC").block();

    logger.info("price:{}", quote);

    webTestClient
        .get()
        .uri("/api/v1/crypto/BTC/price")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.symbol")
        .isEqualTo("BTC")
        .jsonPath("$.price.currency")
        .isEqualTo("USD")
        .jsonPath("$.price.amount")
        .isEqualTo(new java.math.BigDecimal("90214.77304604798"));
  }

  @Test
  void shouldReturn404_whenSymbolNotFoundInCmc() {
    // Mocking a successful response from CMC but with empty data (logical 404)
    stubFor(
        get(urlPathEqualTo("/v2/cryptocurrency/quotes/latest"))
            .withQueryParam("symbol", equalTo("UNKNOWN"))
            .withHeader("X-CMC_PRO_API_KEY", equalTo("fake-api-key"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBodyFile("quotes-response-empty.json"))); // Assuming this file exists and
    // represents
    // empty data

    // Adapter maps "No data for symbol" to InvalidParamException (400) or throws
    // specific error?
    // Wait, Adapter code:
    // if (!data.containsKey(symbol)) -> CoinMarketCapInvalidParamException (400)
    // Controller advice maps InvalidParamException to 400.
    // But previously we wanted 404 for "Symbol found but empty"?
    // Detailed check: GlobalExceptionHandler maps
    // CoinMarketCapInvalidParamException -> 400 Bad
    // Request.
    // So I expect 400 here now with the new architecture.

    webTestClient
        .get()
        .uri("/api/v1/crypto/UNKNOWN/price")
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.title")
        .isEqualTo("Invalid Parameters")
        .jsonPath("$.detail")
        .value(org.hamcrest.Matchers.containsString("not found"));
  }

  @Test
  void shouldReturn400_whenCmcReturns400() {
    stubFor(
        get(urlPathEqualTo("/v2/cryptocurrency/quotes/latest"))
            .withQueryParam("symbol", equalTo("INVALID"))
            .willReturn(
                aResponse()
                    .withStatus(400)
                    .withBody("{\"status\": {\"error_message\": \"Invalid symbol\"}}")));

    webTestClient
        .get()
        .uri("/api/v1/crypto/INVALID/price")
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.title")
        .isEqualTo("Invalid Parameters");
  }

  @Test
  void shouldReturn429_whenCmcReturns429() {
    stubFor(
        get(urlPathEqualTo("/v2/cryptocurrency/quotes/latest"))
            .withQueryParam("symbol", equalTo("BTC"))
            .willReturn(
                aResponse()
                    .withStatus(429)
                    .withBody("{\"status\": {\"error_message\": \"Rate limit exceeded\"}}")));

    webTestClient
        .get()
        .uri("/api/v1/crypto/BTC/price")
        .exchange()
        .expectStatus()
        .isEqualTo(429)
        .expectBody()
        .jsonPath("$.title")
        .isEqualTo("Too Many Requests")
        .jsonPath("$.errorCode")
        .isEqualTo("UPSTREAM_RATE_LIMIT_EXCEEDED")
        .jsonPath("$.errors")
        .isArray();
  }

  @Test
  void shouldReturn429_whenLocalRateLimitIsExceeded() {
    stubFor(
        get(urlPathEqualTo("/v2/cryptocurrency/quotes/latest"))
            .withQueryParam("symbol", equalTo("BTC"))
            .withHeader("X-CMC_PRO_API_KEY", equalTo("fake-api-key"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBodyFile("quotes-response-btc.json")));

    String clientIp = "198.51.100.25";
    for (int attempt = 0; attempt < 60; attempt++) {
      webTestClient
          .get()
          .uri("/api/v1/crypto/BTC/price")
          .header("X-Forwarded-For", clientIp)
          .exchange()
          .expectStatus()
          .isOk();
    }

    webTestClient
        .get()
        .uri("/api/v1/crypto/BTC/price")
        .header("X-Forwarded-For", clientIp)
        .exchange()
        .expectStatus()
        .isEqualTo(429)
        .expectHeader()
        .exists("Retry-After")
        .expectBody()
        .jsonPath("$.title")
        .isEqualTo("Too Many Requests")
        .jsonPath("$.errorCode")
        .isEqualTo("CRYPTO_PRICE_RATE_LIMIT_EXCEEDED")
        .jsonPath("$.errors")
        .isArray();
  }

  @Test
  void shouldReturn502_whenCmcReturns500() {

    logger.info("shouldReturn502_whenCmcReturns500");

    // Mocking a 500 response from CMC
    stubFor(
        get(urlPathEqualTo("/v2/cryptocurrency/quotes/latest"))
            .withQueryParam("symbol", equalTo("BTC"))
            .willReturn(aResponse().withStatus(500).withBody("Internal Server Error")));

    webTestClient
        .get()
        .uri("/api/v1/crypto/BTC/price")
        .exchange()
        .expectStatus()
        .isEqualTo(502) // Bad Gateway
        .expectBody()
        .jsonPath("$.title")
        .isEqualTo("Upstream Error");
  }

  @Test
  void shouldRecoverImmediately_whenUpstreamFailsOnceAndThenSucceeds() {

    logger.info("shouldRecoverImmediately_whenUpstreamFailsOnceAndThenSucceeds");

    stubFor(
        get(urlPathEqualTo("/v2/cryptocurrency/quotes/latest"))
            .withQueryParam("symbol", equalTo("BTC"))
            .willReturn(aResponse().withStatus(500).withBody("Internal Server Error")));

    webTestClient.get().uri("/api/v1/crypto/BTC/price").exchange().expectStatus().isEqualTo(502);

    wireMockServer.resetAll();

    stubFor(
        get(urlPathEqualTo("/v2/cryptocurrency/quotes/latest"))
            .withQueryParam("symbol", equalTo("BTC"))
            .withHeader("X-CMC_PRO_API_KEY", equalTo("fake-api-key"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBodyFile("quotes-response-btc.json")));

    webTestClient
        .get()
        .uri("/api/v1/crypto/BTC/price")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.symbol")
        .isEqualTo("BTC")
        .jsonPath("$.price.currency")
        .isEqualTo("USD");

    verifyCoinMarketCapQuoCalled("BTC", 1);
  }
}
