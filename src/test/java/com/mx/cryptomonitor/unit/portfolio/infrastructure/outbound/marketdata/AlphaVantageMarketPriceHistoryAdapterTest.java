package com.mx.cryptomonitor.unit.portfolio.infrastructure.outbound.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import com.mx.cryptomonitor.portfolio.application.service.HoldingsHistoryRange;
import com.mx.cryptomonitor.portfolio.domain.model.AssetType;
import com.mx.cryptomonitor.portfolio.domain.model.PricePoint;
import com.mx.cryptomonitor.portfolio.infrastructure.outbound.marketdata.AlphaVantageMarketPriceHistoryAdapter;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

class AlphaVantageMarketPriceHistoryAdapterTest {

  private MockWebServer mockWebServer;
  private AlphaVantageMarketPriceHistoryAdapter adapter;

  @BeforeEach
  void setUp() throws Exception {
    mockWebServer = new MockWebServer();
    mockWebServer.start();
    adapter =
        new AlphaVantageMarketPriceHistoryAdapter(
            WebClient.builder(), mockWebServer.url("/").toString(), "demo");
  }

  @AfterEach
  void tearDown() throws Exception {
    mockWebServer.shutdown();
  }

  @Test
  void shouldParseHistoricalSeriesForStockAssets() {
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody(
                """
                {
                  "Time Series (Daily)": {
                    "2026-01-03": {"4. close": "192.20"},
                    "2026-01-02": {"4. close": "190.10"},
                    "2026-01-01": {"4. close": "188.50"}
                  }
                }
                """));

    List<PricePoint> points =
        adapter.fetchPriceHistory(AssetType.STOCK, "AAPL", HoldingsHistoryRange.parse("1y"));

    assertThat(points).hasSize(3);
    assertThat(points.getFirst().price()).isEqualByComparingTo(new BigDecimal("188.50"));
    assertThat(points.getLast().price()).isEqualByComparingTo(new BigDecimal("192.20"));
  }
}
