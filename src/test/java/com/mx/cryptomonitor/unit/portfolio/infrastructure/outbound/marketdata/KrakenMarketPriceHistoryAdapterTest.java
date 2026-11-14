package com.mx.cryptomonitor.unit.portfolio.infrastructure.outbound.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import com.mx.cryptomonitor.portfolio.domain.exception.UnknownAssetSymbolException;
import com.mx.cryptomonitor.portfolio.domain.model.AssetType;
import com.mx.cryptomonitor.portfolio.domain.model.ChartResolution;
import com.mx.cryptomonitor.portfolio.domain.model.PricePoint;
import com.mx.cryptomonitor.portfolio.infrastructure.outbound.marketdata.KrakenMarketPriceHistoryAdapter;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

class KrakenMarketPriceHistoryAdapterTest {

  private MockWebServer mockWebServer;
  private KrakenMarketPriceHistoryAdapter adapter;

  @BeforeEach
  void setUp() throws Exception {
    mockWebServer = new MockWebServer();
    mockWebServer.start();
    adapter =
        new KrakenMarketPriceHistoryAdapter(
            WebClient.builder().baseUrl(mockWebServer.url("/").toString()).build(),
            Clock.systemUTC());
  }

  @AfterEach
  void tearDown() throws Exception {
    mockWebServer.shutdown();
  }

  @Test
  void mapsDisplayKeyedCandleCloseAndRequestsCanonicalPair() throws Exception {
    long t0 = Instant.parse("2026-01-01T00:00:00Z").getEpochSecond();
    long t1 = Instant.parse("2026-01-02T00:00:00Z").getEpochSecond();
    mockWebServer.enqueue(
        json(
            """
            {"error":[],"result":{"BTC/USD":[\
            [%d,"49000.0","51000.0","48500.0","50500.0","50000.0","1.5",10],\
            [%d,"50500.0","52000.0","50000.0","51750.0","51000.0","2.0",12]],"last":%d}}
            """
                .formatted(t0, t1, t1)));

    List<PricePoint> result = adapter.fetchPriceHistory(AssetType.CRYPTO, "BTC", dailyOneDay());

    assertThat(result)
        .containsExactly(
            new PricePoint(Instant.ofEpochSecond(t0), new BigDecimal("50500.0")),
            new PricePoint(Instant.ofEpochSecond(t1), new BigDecimal("51750.0")));

    RecordedRequest request = mockWebServer.takeRequest();
    assertThat(request.getRequestUrl().encodedPath()).isEqualTo("/0/public/OHLC");
    assertThat(request.getRequestUrl().queryParameter("pair")).isEqualTo("BTCUSD");
    assertThat(request.getRequestUrl().queryParameter("assetVersion")).isEqualTo("1");
    assertThat(request.getRequestUrl().queryParameter("interval")).isEqualTo("1440");
  }

  @Test
  void unknownPairIsMappedToRecoverableExceptionForFallback() {
    mockWebServer.enqueue(json("{\"error\":[\"EQuery:Unknown asset pair\"],\"result\":{}}"));

    assertThatThrownBy(() -> adapter.fetchPriceHistory(AssetType.CRYPTO, "NOPE", dailyOneDay()))
        .isInstanceOf(UnknownAssetSymbolException.class);
  }

  @Test
  void disabledAdapterSignalsFallback() {
    KrakenMarketPriceHistoryAdapter disabled =
        new KrakenMarketPriceHistoryAdapter(
            WebClient.builder().baseUrl(mockWebServer.url("/").toString()).build(),
            false,
            Duration.ofSeconds(8),
            Clock.systemUTC());

    assertThatThrownBy(() -> disabled.fetchPriceHistory(AssetType.CRYPTO, "BTC", dailyOneDay()))
        .isInstanceOf(UnknownAssetSymbolException.class);
  }

  private ChartResolution dailyOneDay() {
    return new ChartResolution(
        Instant.parse("2026-01-01T00:00:00Z"),
        Instant.parse("2026-01-02T00:00:00Z"),
        Duration.ofDays(1),
        "1d",
        1);
  }

  private MockResponse json(String body) {
    return new MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody(body);
  }
}
