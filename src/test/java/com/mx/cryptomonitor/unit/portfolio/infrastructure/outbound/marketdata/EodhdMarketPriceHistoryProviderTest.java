package com.mx.cryptomonitor.unit.portfolio.infrastructure.outbound.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import com.mx.cryptomonitor.portfolio.application.service.HoldingsHistoryRange;
import com.mx.cryptomonitor.portfolio.domain.exception.MarketDataRateLimitException;
import com.mx.cryptomonitor.portfolio.domain.exception.MarketDataServerException;
import com.mx.cryptomonitor.portfolio.domain.exception.UnknownAssetSymbolException;
import com.mx.cryptomonitor.portfolio.domain.model.AssetType;
import com.mx.cryptomonitor.portfolio.domain.model.PricePoint;
import com.mx.cryptomonitor.portfolio.infrastructure.outbound.marketdata.EodhdMarketPriceHistoryProvider;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

class EodhdMarketPriceHistoryProviderTest {

  private MockWebServer server;
  private EodhdMarketPriceHistoryProvider adapter;

  @BeforeEach
  void startServer() throws Exception {
    server = new MockWebServer();
    server.start();
    String baseUrl = stripTrailingSlash(server.url("/").toString());
    adapter =
        new EodhdMarketPriceHistoryProvider(WebClient.builder(), baseUrl, "demo-token", ".US");
  }

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
  }

  private void enqueueJson(int code, String body) {
    server.enqueue(
        new MockResponse()
            .setResponseCode(code)
            .addHeader("Content-Type", "application/json")
            .setBody(body));
  }

  @Test
  void returnsAdjustedCloseAndAppendsUsSuffix() throws Exception {
    enqueueJson(
        200,
        """
        [{"date":"2026-10-22","open":1801.0,"high":1824.0,"low":1792.0,"close":1810.15,
          "adjusted_close":1808.00,"volume":345680},
         {"date":"2026-10-23","open":1810.0,"high":1830.0,"low":1805.0,"close":1820.00,
          "adjusted_close":1818.50,"volume":300000}]
        """);

    List<PricePoint> points =
        adapter.fetchPriceHistory(AssetType.STOCK, "MELI", HoldingsHistoryRange.parse("6m"));

    assertThat(points).hasSize(2);
    assertThat(points.get(0).price()).isEqualByComparingTo(new BigDecimal("1808.00"));
    assertThat(points.get(1).price()).isEqualByComparingTo(new BigDecimal("1818.50"));
    assertThat(points.get(0).time()).isBefore(points.get(1).time());
    String path = server.takeRequest().getPath();
    assertThat(path).contains("/api/eod/MELI.US").contains("period=d");
  }

  @Test
  void fallsBackToCloseWhenAdjustedCloseMissing() {
    enqueueJson(200, "[{\"date\":\"2026-10-23\",\"close\":33848.82}]");

    List<PricePoint> points =
        adapter.fetchPriceHistory(AssetType.STOCK, "MELIN.MX", HoldingsHistoryRange.parse("1m"));

    assertThat(points).hasSize(1);
    assertThat(points.get(0).price()).isEqualByComparingTo(new BigDecimal("33848.82"));
  }

  @Test
  void respectsExplicitMarketSuffix() throws Exception {
    enqueueJson(200, "[{\"date\":\"2026-10-23\",\"close\":33848.82,\"adjusted_close\":33848.82}]");

    adapter.fetchPriceHistory(AssetType.STOCK, "MELIN.MX", HoldingsHistoryRange.parse("6m"));

    assertThat(server.takeRequest().getPath()).contains("/api/eod/MELIN.MX");
  }

  @Test
  void emptyArrayThrowsUnknownSymbolToAllowFallback() {
    enqueueJson(200, "[]");

    assertThatThrownBy(
            () ->
                adapter.fetchPriceHistory(
                    AssetType.STOCK, "NOPE", HoldingsHistoryRange.parse("6m")))
        .isInstanceOf(UnknownAssetSymbolException.class);
  }

  @Test
  void notFoundThrowsUnknownSymbolToAllowFallback() {
    enqueueJson(404, "{\"message\":\"Symbol not found\"}");

    assertThatThrownBy(
            () ->
                adapter.fetchPriceHistory(
                    AssetType.STOCK, "NOPE", HoldingsHistoryRange.parse("6m")))
        .isInstanceOf(UnknownAssetSymbolException.class);
  }

  @Test
  void rateLimitOn429() {
    enqueueJson(429, "{\"message\":\"Too many requests\"}");

    assertThatThrownBy(
            () ->
                adapter.fetchPriceHistory(
                    AssetType.STOCK, "MELI", HoldingsHistoryRange.parse("6m")))
        .isInstanceOf(MarketDataRateLimitException.class);
  }

  @Test
  void serverErrorOn500() {
    enqueueJson(500, "{\"message\":\"Internal error\"}");

    assertThatThrownBy(
            () ->
                adapter.fetchPriceHistory(
                    AssetType.STOCK, "MELI", HoldingsHistoryRange.parse("6m")))
        .isInstanceOf(MarketDataServerException.class);
  }

  @Test
  void geoblockStatusesAreRecoverable() {
    for (int status : new int[] {451, 403, 418}) {
      enqueueJson(status, "blocked");
      assertThatThrownBy(
              () ->
                  adapter.fetchPriceHistory(
                      AssetType.STOCK, "MELI", HoldingsHistoryRange.parse("6m")))
          .as("HTTP %s debe ser recuperable", status)
          .isInstanceOf(MarketDataServerException.class);
    }
  }

  private String stripTrailingSlash(String baseUrl) {
    return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
  }
}
