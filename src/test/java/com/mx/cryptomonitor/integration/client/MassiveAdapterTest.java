package com.mx.cryptomonitor.integration.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import com.mx.cryptomonitor.marketdata.domain.exception.MassiveRateLimitException;
import com.mx.cryptomonitor.marketdata.domain.exception.MassiveServerException;
import com.mx.cryptomonitor.marketdata.infrastructure.outbound.massive.MassiveAdapter;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

class MassiveAdapterTest {

  private MockWebServer mockWebServer;
  private MassiveAdapter adapter;

  @BeforeEach
  void startServer() throws Exception {
    mockWebServer = new MockWebServer();
    mockWebServer.start();

    String baseUrl = mockWebServer.url("/").toString();
    adapter =
        new MassiveAdapter(WebClient.builder().build(), stripTrailingSlash(baseUrl), "demo-key");
  }

  @AfterEach
  void tearDown() throws Exception {
    mockWebServer.shutdown();
  }

  @Test
  void getLatestReturnsPreviousCloseWhenProviderRespondsOk() {
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody(
                """
                {
                  "adjusted": true,
                  "results": [
                    {
                      "T": "AAPL",
                      "c": 115.97
                    }
                  ],
                  "resultsCount": 1,
                  "status": "OK",
                  "ticker": "AAPL"
                }
                """));

    Optional<BigDecimal> result = adapter.getLatest("AAPL");

    assertThat(result).contains(new BigDecimal("115.97"));
  }

  @Test
  void getLatestReturnsEmptyWhenProviderHasNoResults() {
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody(
                """
                {
                  "results": [],
                  "resultsCount": 0,
                  "status": "OK",
                  "ticker": "UNKNOWN"
                }
                """));

    Optional<BigDecimal> result = adapter.getLatest("UNKNOWN");

    assertThat(result).isEmpty();
  }

  @Test
  void getLatestThrowsRateLimitExceptionWhenMassiveMentionsQuota() {
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(429)
            .addHeader("Content-Type", "application/json")
            .setBody(
                """
                {
                  "status": "ERROR",
                  "error": "Rate limit exceeded for current plan"
                }
                """));

    assertThatThrownBy(() -> adapter.getLatest("AAPL"))
        .isInstanceOf(MassiveRateLimitException.class)
        .hasMessageContaining("Rate limit exceeded");
  }

  @Test
  void getLatestThrowsRateLimitExceptionWhenMassiveMentionsRequestsPerMinute() {
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(429)
            .addHeader("Content-Type", "application/json")
            .setBody(
                """
                {
                  "status": "ERROR",
                  "message": "You've exceeded the maximum requests per minute, please wait or upgrade your subscription to continue."
                }
                """));

    assertThatThrownBy(() -> adapter.getLatest("AAPL"))
        .isInstanceOf(MassiveRateLimitException.class)
        .hasMessageContaining("maximum requests per minute");
  }

  @Test
  void getLatestThrowsServerExceptionWhenMassiveReturnsNonOkStatus() {
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(500)
            .addHeader("Content-Type", "application/json")
            .setBody(
                """
                {
                  "status": "ERROR",
                  "message": "Temporary provider outage"
                }
                """));

    assertThatThrownBy(() -> adapter.getLatest("AAPL"))
        .isInstanceOf(MassiveServerException.class)
        .hasMessageContaining("Temporary provider outage");
  }

  private String stripTrailingSlash(String baseUrl) {
    return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
  }
}
