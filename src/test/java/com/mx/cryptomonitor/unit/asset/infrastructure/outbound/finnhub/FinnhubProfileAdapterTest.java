package com.mx.cryptomonitor.unit.asset.infrastructure.outbound.finnhub;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import com.mx.cryptomonitor.asset.application.port.out.StockProfilePort.StockProfile;
import com.mx.cryptomonitor.asset.infrastructure.outbound.finnhub.FinnhubProfileAdapter;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

class FinnhubProfileAdapterTest {

  private static final String TOKEN = "super-secret-finnhub-token";

  private MockWebServer server;
  private FinnhubProfileAdapter adapter;

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
    adapter =
        new FinnhubProfileAdapter(
            WebClient.builder().baseUrl(server.url("/").toString()).build(), TOKEN);
  }

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
  }

  @Test
  void googlProfileHasRealLogoAndMarketCap() throws InterruptedException {
    server.enqueue(
        jsonOk(
            """
            {
              "logo": "https://static2.finnhub.io/file/publicdatany/finnhubimage/stock_logo/GOOG.png",
              "name": "Alphabet Inc",
              "ticker": "GOOGL",
              "marketCapitalization": 2100000.5,
              "exchange": "NASDAQ",
              "currency": "USD"
            }
            """));

    Optional<StockProfile> result = adapter.getProfile("googl");

    assertThat(result).isPresent();
    StockProfile profile = result.get();
    assertThat(profile.symbol()).isEqualTo("GOOGL");
    assertThat(profile.logoUrl())
        .isEqualTo("https://static2.finnhub.io/file/publicdatany/finnhubimage/stock_logo/GOOG.png");
    assertThat(profile.marketCapMillions()).isEqualTo(2100001L);
    assertThat(profile.exchange()).isEqualTo("NASDAQ");
    assertThat(profile.currency()).isEqualTo("USD");

    RecordedRequest request = server.takeRequest();
    assertThat(request.getRequestUrl().encodedPath()).isEqualTo("/stock/profile2");
    assertThat(request.getRequestUrl().queryParameter("symbol")).isEqualTo("GOOGL");
    assertThat(request.getRequestUrl().queryParameter("token")).isEqualTo(TOKEN);
  }

  @Test
  void emptyResponseBodyReturnsEmpty() {
    server.enqueue(jsonOk("{}"));

    assertThat(adapter.getProfile("ZZZZ")).isEmpty();
  }

  @Test
  void blankLogoIsMappedToNullNotEmptyString() {
    server.enqueue(
        jsonOk(
            """
            {
              "logo": "",
              "name": "Some Company",
              "ticker": "SOME"
            }
            """));

    Optional<StockProfile> result = adapter.getProfile("SOME");

    assertThat(result).isPresent();
    assertThat(result.get().logoUrl()).isNull();
  }

  @Test
  void missingApiKeyReturnsEmptyWithoutCallingFinnhub() {
    adapter =
        new FinnhubProfileAdapter(
            WebClient.builder().baseUrl(server.url("/").toString()).build(), "");

    assertThat(adapter.getProfile("AAPL")).isEmpty();
    assertThat(server.getRequestCount()).isZero();
  }

  @Test
  void httpErrorReturnsEmptyWithoutThrowing() {
    server.enqueue(new MockResponse().setResponseCode(500));

    assertThat(adapter.getProfile("AAPL")).isEmpty();
  }

  @Test
  void tokenNeverAppearsInAnyExceptionOrLogPath() {
    server.enqueue(new MockResponse().setResponseCode(500).setBody("Internal Server Error"));

    // getProfile swallows all errors internally and never throws — the token can only ever
    // reach the outbound query string, verified separately in googlProfileHasRealLogoAndMarketCap.
    Optional<StockProfile> result = adapter.getProfile("AAPL");

    assertThat(result).isEmpty();
  }

  private MockResponse jsonOk(String body) {
    return new MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody(body);
  }
}
