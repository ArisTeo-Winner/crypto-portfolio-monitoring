package com.mx.cryptomonitor.integration.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import com.mx.cryptomonitor.marketdata.domain.exception.ExternalProviderRateLimitException;
import com.mx.cryptomonitor.marketdata.domain.exception.ExternalProviderUpstreamException;
import com.mx.cryptomonitor.marketdata.infrastructure.outbound.eodhd.EodhdAdapter;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

class EodhdAdapterTest {

  private MockWebServer mockWebServer;
  private EodhdAdapter adapter;

  @BeforeEach
  void startServer() throws Exception {
    mockWebServer = new MockWebServer();
    mockWebServer.start();
    String baseUrl = stripTrailingSlash(mockWebServer.url("/").toString());
    adapter = new EodhdAdapter(WebClient.builder().build(), baseUrl, "demo-token", ".US");
  }

  @AfterEach
  void tearDown() throws Exception {
    mockWebServer.shutdown();
  }

  private void enqueueJson(int code, String body) {
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(code)
            .addHeader("Content-Type", "application/json")
            .setBody(body));
  }

  @Test
  void getLatestReturnsCloseAndAppendsDefaultUsSuffix() throws Exception {
    enqueueJson(
        200,
        """
        {"code":"MELI.US","timestamp":1792785480,"open":1801.01,"high":1824.06,"low":1792.72,
         "close":1810.15,"volume":345680,"previousClose":1824.96,"change":-14.81}
        """);

    Optional<BigDecimal> result = adapter.getLatest("MELI");

    assertThat(result).contains(new BigDecimal("1810.15"));
    assertThat(mockWebServer.takeRequest().getPath()).contains("/api/real-time/MELI.US");
  }

  @Test
  void getLatestRespectsExplicitMarketSuffix() throws Exception {
    enqueueJson(200, "{\"code\":\"MELIN.MX\",\"close\":33848.82,\"previousClose\":33723.58}");

    Optional<BigDecimal> result = adapter.getLatest("MELIN.MX");

    assertThat(result).contains(new BigDecimal("33848.82"));
    assertThat(mockWebServer.takeRequest().getPath()).contains("/api/real-time/MELIN.MX");
  }

  @Test
  void getLatestReturnsEmptyWhenCloseIsNa() {
    enqueueJson(200, "{\"code\":\"UNKN.US\",\"close\":\"NA\",\"previousClose\":\"NA\"}");

    assertThat(adapter.getLatest("UNKN")).isEmpty();
  }

  @Test
  void getLatestReturnsEmptyOnNotFoundToAllowFallback() {
    enqueueJson(404, "{\"message\":\"Symbol not found\"}");

    assertThat(adapter.getLatest("NOPE")).isEmpty();
  }

  @Test
  void getLatestThrowsRateLimitOn429() {
    enqueueJson(429, "{\"message\":\"Too many requests\"}");

    assertThatThrownBy(() -> adapter.getLatest("MELI"))
        .isInstanceOf(ExternalProviderRateLimitException.class);
  }

  @Test
  void getLatestThrowsUpstreamOn500() {
    enqueueJson(500, "{\"message\":\"Internal error\"}");

    assertThatThrownBy(() -> adapter.getLatest("MELI"))
        .isInstanceOf(ExternalProviderUpstreamException.class);
  }

  private String stripTrailingSlash(String baseUrl) {
    return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
  }
}
