package com.mx.cryptomonitor.unit.marketdata.infrastructure.outbound.cmc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import com.mx.cryptomonitor.marketdata.domain.model.AssetType;
import com.mx.cryptomonitor.marketdata.domain.model.ProviderId;
import com.mx.cryptomonitor.marketdata.infrastructure.configuration.CoinMarketCapProperties;
import com.mx.cryptomonitor.marketdata.infrastructure.outbound.cmc.CmcErrorType;
import com.mx.cryptomonitor.marketdata.infrastructure.outbound.cmc.CoinMarketCapClientException;
import com.mx.cryptomonitor.marketdata.infrastructure.outbound.cmc.CoinMarketCapReactiveClient;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

class CoinMarketCapReactiveClientTest {

  private static MockWebServer mockWebServer;

  @BeforeAll
  static void setUp() throws Exception {
    mockWebServer = new MockWebServer();
    mockWebServer.start();
  }

  @AfterAll
  static void tearDown() throws Exception {
    mockWebServer.shutdown();
  }

  @Test
  void getLatestQuotesRawShouldNormalizeSymbolAndReturnResponse() throws Exception {
    CoinMarketCapReactiveClient client = newClient("USD", true, Duration.ofSeconds(2));
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
                          "USD": {"price": 69512.67}
                        }
                      }
                    ]
                  }
                }
                """));

    var result = client.getLatestQuotesRaw(" btc ").block();
    var recordedRequest = mockWebServer.takeRequest();

    assertThat(result).isNotNull();
    assertThat(recordedRequest.getPath()).isEqualTo("/v2/cryptocurrency/quotes/latest?symbol=BTC");
    assertThat(result.data()).containsKey("BTC");
  }

  @Test
  void getLatestQuotesRawShouldFailWhenIntegrationIsDisabled() {
    CoinMarketCapReactiveClient client = newClient("USD", false, Duration.ofSeconds(2));

    assertThatThrownBy(() -> client.getLatestQuotesRaw("BTC").block())
        .isInstanceOf(CoinMarketCapClientException.class)
        .satisfies(
            throwable -> {
              CoinMarketCapClientException ex = (CoinMarketCapClientException) throwable;
              assertThat(ex.getType()).isEqualTo(CmcErrorType.SERVER_ERROR);
              assertThat(ex.getHttpStatus()).isEqualTo(503);
            });
  }

  @Test
  void getLatestQuotesRawShouldRejectBlankSymbol() {
    CoinMarketCapReactiveClient client = newClient("USD", true, Duration.ofSeconds(2));

    assertThatThrownBy(() -> client.getLatestQuotesRaw(" ").block())
        .isInstanceOf(CoinMarketCapClientException.class)
        .satisfies(
            throwable -> {
              CoinMarketCapClientException ex = (CoinMarketCapClientException) throwable;
              assertThat(ex.getType()).isEqualTo(CmcErrorType.BAD_REQUEST);
              assertThat(ex.getHttpStatus()).isEqualTo(400);
            });
  }

  @Test
  void getLatestCryptoUsdQuoteShouldFailWhenIntegrationIsDisabled() {
    CoinMarketCapReactiveClient client = newClient("USD", false, Duration.ofSeconds(2));

    assertThatThrownBy(() -> client.getLatestCryptoUsdQuote("BTC").block())
        .isInstanceOf(CoinMarketCapClientException.class)
        .satisfies(
            throwable -> {
              CoinMarketCapClientException ex = (CoinMarketCapClientException) throwable;
              assertThat(ex.getType()).isEqualTo(CmcErrorType.SERVER_ERROR);
              assertThat(ex.getHttpStatus()).isEqualTo(503);
            });
  }

  @Test
  void getLatestCryptoUsdQuoteShouldRejectUnsupportedConversionCurrency() {
    CoinMarketCapReactiveClient client = newClient("EUR", true, Duration.ofSeconds(2));

    assertThatThrownBy(() -> client.getLatestCryptoUsdQuote("BTC").block())
        .isInstanceOf(CoinMarketCapClientException.class)
        .satisfies(
            throwable -> {
              CoinMarketCapClientException ex = (CoinMarketCapClientException) throwable;
              assertThat(ex.getType()).isEqualTo(CmcErrorType.BAD_REQUEST);
              assertThat(ex.getRawBody()).isEqualTo("EUR");
            });
  }

  @Test
  void getLatestCryptoUsdQuoteShouldUseUsdWhenDefaultConvertIsNull() {
    CoinMarketCapReactiveClient client = newClient(null, true, Duration.ofSeconds(2));
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
                          "USD": {"price": 69512.67}
                        }
                      }
                    ]
                  }
                }
                """));

    var result = client.getLatestCryptoUsdQuote("BTC").block();

    assertThat(result).isNotNull();
    assertThat(result.price().currency()).isEqualTo("USD");
  }

  @Test
  void getLatestCryptoUsdQuoteShouldFailWhenStatusContainsApplicationError() {
    CoinMarketCapReactiveClient client = newClient("USD", true, Duration.ofSeconds(2));
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody(
                """
                {
                  "status": {
                    "error_code": 1001,
                    "error_message": "symbol not found"
                  },
                  "data": {}
                }
                """));

    assertThatThrownBy(() -> client.getLatestCryptoUsdQuote("BTC").block())
        .isInstanceOf(CoinMarketCapClientException.class)
        .satisfies(
            throwable -> {
              CoinMarketCapClientException ex = (CoinMarketCapClientException) throwable;
              assertThat(ex.getType()).isEqualTo(CmcErrorType.SERVER_ERROR);
              assertThat(ex.getProviderErrorCode()).isEqualTo(1001);
              assertThat(ex.getProviderErrorMessage()).isEqualTo("symbol not found");
            });
  }

  @Test
  void getLatestCryptoUsdQuoteShouldFailWhenDataForSymbolIsMissing() {
    CoinMarketCapReactiveClient client = newClient("USD", true, Duration.ofSeconds(2));
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

    assertThatThrownBy(() -> client.getLatestCryptoUsdQuote("BTC").block())
        .isInstanceOf(CoinMarketCapClientException.class)
        .satisfies(
            throwable -> {
              CoinMarketCapClientException ex = (CoinMarketCapClientException) throwable;
              assertThat(ex.getType()).isEqualTo(CmcErrorType.DATA_NOT_FOUND);
              assertThat(ex.getMessage()).contains("No data for symbol BTC");
            });
  }

  @Test
  void getLatestCryptoUsdQuoteShouldFailWhenUsdQuoteKeyIsMissing() {
    CoinMarketCapReactiveClient client = newClient("USD", true, Duration.ofSeconds(2));
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
                          "EUR": {"price": 60000.00}
                        }
                      }
                    ]
                  }
                }
                """));

    assertThatThrownBy(() -> client.getLatestCryptoUsdQuote("BTC").block())
        .isInstanceOf(CoinMarketCapClientException.class)
        .satisfies(
            throwable -> {
              CoinMarketCapClientException ex = (CoinMarketCapClientException) throwable;
              assertThat(ex.getType()).isEqualTo(CmcErrorType.DATA_NOT_FOUND);
              assertThat(ex.getMessage()).contains("No USD quote for symbol BTC");
            });
  }

  @Test
  void getLatestCryptoUsdQuoteShouldFailWhenUsdPriceIsMissing() {
    CoinMarketCapReactiveClient client = newClient("USD", true, Duration.ofSeconds(2));
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
                          "USD": {"price": null}
                        }
                      }
                    ]
                  }
                }
                """));

    assertThatThrownBy(() -> client.getLatestCryptoUsdQuote("BTC").block())
        .isInstanceOf(CoinMarketCapClientException.class)
        .satisfies(
            throwable -> {
              CoinMarketCapClientException ex = (CoinMarketCapClientException) throwable;
              assertThat(ex.getType()).isEqualTo(CmcErrorType.DATA_NOT_FOUND);
              assertThat(ex.getMessage()).contains("USD quote without price");
            });
  }

  @Test
  void getLatestCryptoUsdQuoteShouldReturnPriceQuoteWhenResponseIsValid() {
    CoinMarketCapReactiveClient client = newClient("USD", true, Duration.ofSeconds(2));
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
                          "USD": {"price": 69512.67}
                        }
                      }
                    ]
                  }
                }
                """));

    var result = client.getLatestCryptoUsdQuote("btc").block();

    assertThat(result).isNotNull();
    assertThat(result.symbol()).isEqualTo("BTC");
    assertThat(result.assetType()).isEqualTo(AssetType.CRYPTO);
    assertThat(result.price().amount()).isEqualByComparingTo("69512.67");
    assertThat(result.price().currency()).isEqualTo("USD");
    assertThat(result.provider()).isEqualTo(ProviderId.COINMARKETCAP);
    assertThat(result.asOf()).isNotNull();
  }

  @Test
  void getLatestCrytoUsdQuoteShouldDelegateToLatestCryptoUsdQuote() {
    CoinMarketCapReactiveClient client = newClient("USD", true, Duration.ofSeconds(2));
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
                          "USD": {"price": 69512.67}
                        }
                      }
                    ]
                  }
                }
                """));

    var result = client.getLatestCrytoUsdQuote("BTC").block();

    assertThat(result).isNotNull();
    assertThat(result.symbol()).isEqualTo("BTC");
  }

  private CoinMarketCapReactiveClient newClient(
      String defaultConvert, boolean enabled, Duration responseTimeout) {
    String baseUrl = mockWebServer.url("/").toString();
    WebClient webClient = WebClient.builder().baseUrl(baseUrl).build();
    CoinMarketCapProperties props =
        new CoinMarketCapProperties(
            baseUrl, "demo-key", responseTimeout, Duration.ofMinutes(1), defaultConvert, enabled);
    return new CoinMarketCapReactiveClient(webClient, props);
  }
}
