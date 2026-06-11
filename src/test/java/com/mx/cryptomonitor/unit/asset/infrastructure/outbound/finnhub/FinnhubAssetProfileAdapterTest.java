package com.mx.cryptomonitor.unit.asset.infrastructure.outbound.finnhub;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.web.reactive.function.client.WebClient;

import com.mx.cryptomonitor.asset.application.port.out.AssetProfileProvider.AssetProfile;
import com.mx.cryptomonitor.asset.infrastructure.outbound.finnhub.FinnhubAssetProfileAdapter;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

class FinnhubAssetProfileAdapterTest {

  private MockWebServer server;
  private FinnhubAssetProfileAdapter adapter;

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
    adapter =
        new FinnhubAssetProfileAdapter(
            WebClient.builder().baseUrl(server.url("/").toString()).build(),
            "test-key",
            new ConcurrentMapCacheManager("asset-logos"));
  }

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
  }

  @Test
  void shouldExtractLogoAndCacheProfileBySymbol() throws InterruptedException {
    server.enqueue(
        jsonResponse(
            """
            {
              "logo": "https://static2.finnhub.io/aapl.png",
              "name": "Apple Inc",
              "ticker": "AAPL"
            }
            """));

    Optional<String> firstResult = adapter.getLogoUrl("aapl");
    Optional<String> cachedResult = adapter.getLogoUrl("AAPL");

    assertThat(firstResult).contains("https://static2.finnhub.io/aapl.png");
    assertThat(cachedResult).contains("https://static2.finnhub.io/aapl.png");
    assertThat(server.getRequestCount()).isOne();

    RecordedRequest request = server.takeRequest();
    assertThat(request.getRequestUrl().encodedPath()).isEqualTo("/stock/profile2");
    assertThat(request.getRequestUrl().queryParameter("symbol")).isEqualTo("AAPL");
    assertThat(request.getRequestUrl().queryParameter("token")).isEqualTo("test-key");
  }

  @Test
  void shouldReturnProfileForDynamicStockSearch() {
    server.enqueue(
        jsonResponse(
            """
            {
              "logo": "https://static2.finnhub.io/meta.png",
              "name": "Meta Platforms Inc",
              "ticker": "META"
            }
            """));

    Optional<AssetProfile> result = adapter.getProfile("meta");

    assertThat(result)
        .contains(
            new AssetProfile("META", "Meta Platforms Inc", "https://static2.finnhub.io/meta.png"));
  }

  @Test
  void shouldReturnEmptyWhenApiKeyIsMissingWithoutCallingFinnhub() {
    adapter =
        new FinnhubAssetProfileAdapter(
            WebClient.builder().baseUrl(server.url("/").toString()).build(),
            "",
            new ConcurrentMapCacheManager("asset-logos"));

    assertThat(adapter.getLogoUrl("AAPL")).isEmpty();
    assertThat(server.getRequestCount()).isZero();
  }

  @Test
  void shouldReturnEmptyWhenFinnhubFails() {
    server.enqueue(new MockResponse().setResponseCode(500));

    assertThat(adapter.getLogoUrl("AAPL")).isEmpty();
  }

  @Test
  void shouldReturnEmptyWhenFinnhubRejectsApiKey() {
    server.enqueue(errorResponse(401, "{\"error\":\"Invalid API key\"}"));

    assertThat(adapter.getLogoUrl("AAPL")).isEmpty();
  }

  @Test
  void shouldReturnEmptyWhenFinnhubRateLimitsRequests() {
    server.enqueue(errorResponse(429, "{\"error\":\"Rate limit exceeded\"}"));

    assertThat(adapter.getLogoUrl("AAPL")).isEmpty();
  }

  @Test
  void shouldReturnEmptyWhenFinnhubPlanDoesNotAllowProfile() {
    server.enqueue(
        errorResponse(
            403,
            """
            {
              "error": "Premium Query Parameter: Special Endpoint is not available under your current subscription"
            }
            """));

    assertThat(adapter.getLogoUrl("AAPL")).isEmpty();
  }

  @Test
  void shouldReturnEmptyLogoWhenFinnhubDoesNotProvideOne() {
    server.enqueue(
        jsonResponse(
            """
            {
              "logo": "",
              "name": "Apple Inc",
              "ticker": "AAPL"
            }
            """));

    assertThat(adapter.getLogoUrl("AAPL")).isEmpty();
  }

  private MockResponse jsonResponse(String body) {
    return new MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody(body);
  }

  private MockResponse errorResponse(int statusCode, String body) {
    return new MockResponse()
        .setResponseCode(statusCode)
        .addHeader("Content-Type", "application/json")
        .setBody(body);
  }
}
