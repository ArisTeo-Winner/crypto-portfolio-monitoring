package com.mx.cryptomonitor.unit.marketdata.infrastructure.outbound.cmc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.web.reactive.function.client.WebClient;

import com.mx.cryptomonitor.marketdata.domain.exception.CoinMarketCapInvalidParamException;
import com.mx.cryptomonitor.marketdata.domain.exception.CoinMarketCapRateLimitException;
import com.mx.cryptomonitor.marketdata.domain.exception.CoinMarketCapServerException;
import com.mx.cryptomonitor.marketdata.domain.model.AssetType;
import com.mx.cryptomonitor.marketdata.domain.model.ProviderId;
import com.mx.cryptomonitor.marketdata.infrastructure.configuration.CoinMarketCapProperties;
import com.mx.cryptomonitor.marketdata.infrastructure.outbound.cmc.CoinMarketCapAdapter;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

class CoinMarketCapAdapterTest {

  private static MockWebServer mockWebServer;
  private static SimpleMeterRegistry meterRegistry;

  @BeforeAll
  static void setUp() throws Exception {
    mockWebServer = new MockWebServer();
    mockWebServer.start();
    meterRegistry = new SimpleMeterRegistry();
  }

  @AfterAll
  static void tearDown() throws Exception {
    mockWebServer.shutdown();
    meterRegistry.close();
  }

  @Test
  void getCryptoPriceShouldReturnQuoteWhenResponseIsValid() {
    CoinMarketCapAdapter adapter = newAdapter(true);
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody(
                """
                {
                  "status": {"error_code": 0, "error_message": null},
                  "data": {
                    "BTC": [
                      {
                        "symbol": "BTC",
                        "quote": {
                          "USD": {
                            "price": 69512.67
                          }
                        }
                      }
                    ]
                  }
                }
                """));

    var result = adapter.getCryptoPrice("btc").block();

    assertThat(result).isNotNull();
    assertThat(result.symbol()).isEqualTo("BTC");
    assertThat(result.assetType()).isEqualTo(AssetType.CRYPTO);
    assertThat(result.price().amount()).isEqualByComparingTo("69512.67");
    assertThat(result.price().currency()).isEqualTo("USD");
    assertThat(result.provider()).isEqualTo(ProviderId.COINMARKETCAP);
  }

  @Test
  void getCryptoPriceShouldReturnCachedQuoteWhenSameSymbolIsRequestedAgain() {
    CoinMarketCapAdapter adapter = newAdapter(true);
    int requestsBefore = mockWebServer.getRequestCount();
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody(
                """
                {
                  "status": {"error_code": 0, "error_message": null},
                  "data": {
                    "BTC": [
                      {
                        "symbol": "BTC",
                        "quote": {
                          "USD": {
                            "price": 69512.67
                          }
                        }
                      }
                    ]
                  }
                }
                """));

    var first = adapter.getCryptoPrice("btc").block();
    var second = adapter.getCryptoPrice("BTC").block();

    assertThat(second).isEqualTo(first);
    assertThat(mockWebServer.getRequestCount() - requestsBefore).isEqualTo(1);
  }

  @Test
  void getCryptoPriceShouldFailWhenIntegrationIsDisabled() {
    CoinMarketCapAdapter adapter = newAdapter(false);

    assertThatThrownBy(() -> adapter.getCryptoPrice("BTC").block())
        .isInstanceOf(CoinMarketCapServerException.class)
        .hasMessageContaining("disabled");
  }

  @Test
  void getCryptoPriceShouldRejectBlankSymbol() {
    CoinMarketCapAdapter adapter = newAdapter(true);

    assertThatThrownBy(() -> adapter.getCryptoPrice(" ").block())
        .isInstanceOf(CoinMarketCapInvalidParamException.class)
        .hasMessageContaining("Symbol cannot be empty");
  }

  @Test
  void getCryptoPriceShouldMap429ToRateLimitException() {
    CoinMarketCapAdapter adapter = newAdapter(true);
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(429)
            .addHeader("Content-Type", "text/plain")
            .setBody("rate"));

    assertThatThrownBy(() -> adapter.getCryptoPrice("BTC").block())
        .isInstanceOf(CoinMarketCapRateLimitException.class)
        .hasMessageContaining("Rate limit exceeded");
  }

  @Test
  void getCryptoPriceShouldMap400ToInvalidParamException() {
    CoinMarketCapAdapter adapter = newAdapter(true);
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(400)
            .addHeader("Content-Type", "text/plain")
            .setBody("invalid symbol"));

    assertThatThrownBy(() -> adapter.getCryptoPrice("BTC").block())
        .isInstanceOf(CoinMarketCapInvalidParamException.class)
        .hasMessageContaining("Invalid parameters: invalid symbol");
  }

  @Test
  void getCryptoPriceShouldMapGeneric4xxToInvalidParamException() {
    CoinMarketCapAdapter adapter = newAdapter(true);
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(404)
            .addHeader("Content-Type", "text/plain")
            .setBody("not found"));

    assertThatThrownBy(() -> adapter.getCryptoPrice("BTC").block())
        .isInstanceOf(CoinMarketCapInvalidParamException.class)
        .hasMessageContaining("Client Error 404 NOT_FOUND: not found");
  }

  @Test
  void getCryptoPriceShouldRetryAndFailAfterServerErrors() {
    CoinMarketCapAdapter adapter = newAdapter(true);
    int requestsBefore = mockWebServer.getRequestCount();
    for (int i = 0; i < 4; i++) {
      mockWebServer.enqueue(
          new MockResponse()
              .setResponseCode(500)
              .addHeader("Content-Type", "text/plain")
              .setBody("server down"));
    }

    assertThatThrownBy(() -> adapter.getCryptoPrice("BTC").block())
        .isInstanceOf(CoinMarketCapServerException.class)
        .hasMessageContaining("External service failed after max retries");

    assertThat(mockWebServer.getRequestCount() - requestsBefore).isEqualTo(4);
  }

  @Test
  void getCryptoPriceShouldFailWhenUsdQuoteIsMissing() {
    CoinMarketCapAdapter adapter = newAdapter(true);
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody(
                """
                {
                  "status": {"error_code": 0, "error_message": null},
                  "data": {
                    "BTC": [
                      {
                        "symbol": "BTC",
                        "quote": {
                          "EUR": {
                            "price": 64000.00
                          }
                        }
                      }
                    ]
                  }
                }
                """));

    assertThatThrownBy(() -> adapter.getCryptoPrice("BTC").block())
        .isInstanceOf(CoinMarketCapServerException.class)
        .hasMessageContaining("No USD quote available");
  }

  @Test
  void getCryptoPriceShouldFailWhenResponseContainsApplicationError() {
    CoinMarketCapAdapter adapter = newAdapter(true);
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody(
                """
                {
                  "status": {"error_code": 1001, "error_message": "symbol not found"},
                  "data": {}
                }
                """));

    assertThatThrownBy(() -> adapter.getCryptoPrice("BTC").block())
        .isInstanceOf(CoinMarketCapServerException.class)
        .hasMessageContaining("CMC API Error: symbol not found");
  }

  @Test
  void getCryptoPriceShouldFailWhenSymbolIsMissingFromData() {
    CoinMarketCapAdapter adapter = newAdapter(true);
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody(
                """
                {
                  "status": {"error_code": 0, "error_message": null},
                  "data": {}
                }
                """));

    assertThatThrownBy(() -> adapter.getCryptoPrice("BTC").block())
        .isInstanceOf(CoinMarketCapInvalidParamException.class)
        .hasMessageContaining("Symbol not found: BTC");
  }

  @Test
  void getCryptoPriceShouldFailWhenCryptoListIsEmpty() {
    CoinMarketCapAdapter adapter = newAdapter(true);
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody(
                """
                {
                  "status": {"error_code": 0, "error_message": null},
                  "data": {
                    "BTC": []
                  }
                }
                """));

    assertThatThrownBy(() -> adapter.getCryptoPrice("BTC").block())
        .isInstanceOf(CoinMarketCapInvalidParamException.class)
        .hasMessageContaining("No data for symbol: BTC");
  }

  private CoinMarketCapAdapter newAdapter(boolean enabled) {
    String baseUrl = mockWebServer.url("/").toString();
    WebClient webClient = WebClient.builder().baseUrl(baseUrl).build();
    CoinMarketCapProperties props =
        new CoinMarketCapProperties(
            baseUrl, "demo-key", Duration.ofSeconds(2), Duration.ofMinutes(1), "USD", enabled);
    return new CoinMarketCapAdapter(
        webClient, props, meterRegistry, new ConcurrentMapCacheManager(CoinMarketCapAdapter.CACHE_NAME));
  }
}
