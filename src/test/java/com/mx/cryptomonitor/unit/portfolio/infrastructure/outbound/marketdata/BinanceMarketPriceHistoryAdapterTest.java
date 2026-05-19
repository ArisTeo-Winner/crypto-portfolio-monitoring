package com.mx.cryptomonitor.unit.portfolio.infrastructure.outbound.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import com.mx.cryptomonitor.portfolio.application.service.HoldingsHistoryRange;
import com.mx.cryptomonitor.portfolio.domain.model.AssetType;
import com.mx.cryptomonitor.portfolio.domain.model.PricePoint;
import com.mx.cryptomonitor.portfolio.infrastructure.outbound.marketdata.BinanceMarketPriceHistoryAdapter;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

class BinanceMarketPriceHistoryAdapterTest {

  // Fixed "now" so start/end range assertions are deterministic
  private static final Instant FIXED_NOW = Instant.parse("2026-01-31T00:00:00Z");
  private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

  private MockWebServer mockWebServer;
  private BinanceMarketPriceHistoryAdapter adapter;

  @BeforeEach
  void setUp() throws Exception {
    mockWebServer = new MockWebServer();
    mockWebServer.start();
    adapter =
        new BinanceMarketPriceHistoryAdapter(
            WebClient.builder().baseUrl(mockWebServer.url("/").toString()).build(), FIXED_CLOCK);
  }

  @AfterEach
  void tearDown() throws Exception {
    mockWebServer.shutdown();
  }

  @Test
  void fetchPriceHistoryComputesStartEndRelativeToFixedClock() throws Exception {
    // One kline: open-time, open, high, low, close, ...
    long openTimeMs = Instant.parse("2026-01-30T00:00:00Z").toEpochMilli();
    mockWebServer.enqueue(
        json(
            """
            [
              [%d, "50000.00", "51000.00", "49000.00", "50500.00", "1.5", %d, "75750.00", 100, "0.8", "40400.00", "0"]
            ]
            """
                .formatted(openTimeMs, openTimeMs + 86_400_000L)));
    // Empty page to stop pagination
    mockWebServer.enqueue(json("[]"));

    java.util.List<PricePoint> result =
        adapter.fetchPriceHistory(AssetType.CRYPTO, "BTC", HoldingsHistoryRange.parse("30d"));

    assertThat(result)
        .containsExactly(
            new PricePoint(Instant.parse("2026-01-30T00:00:00Z"), new BigDecimal("50500.00")));

    RecordedRequest request = mockWebServer.takeRequest();
    assertThat(request.getRequestUrl().encodedPath()).isEqualTo("/api/v3/klines");
    assertThat(request.getRequestUrl().queryParameter("symbol")).isEqualTo("BTCUSDT");

    long startTime = Long.parseLong(request.getRequestUrl().queryParameter("startTime"));
    long endTime = Long.parseLong(request.getRequestUrl().queryParameter("endTime"));

    // With Clock.fixed at 2026-01-31T00:00:00Z and range=30d:
    //   end   = 2026-01-31T00:00:00Z → epoch ms 1738281600000
    //   start = 2026-01-01T00:00:00Z → epoch ms 1735689600000
    assertThat(endTime).isEqualTo(FIXED_NOW.toEpochMilli());
    assertThat(startTime).isEqualTo(FIXED_NOW.minus(Duration.ofDays(30)).toEpochMilli());
  }

  private MockResponse json(String body) {
    return new MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody(body);
  }
}
