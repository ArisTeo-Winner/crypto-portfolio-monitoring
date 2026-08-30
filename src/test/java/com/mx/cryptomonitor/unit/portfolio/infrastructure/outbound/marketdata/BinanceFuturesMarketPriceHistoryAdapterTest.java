package com.mx.cryptomonitor.unit.portfolio.infrastructure.outbound.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import com.mx.cryptomonitor.portfolio.application.service.HoldingsHistoryRange;
import com.mx.cryptomonitor.portfolio.domain.exception.UnknownAssetSymbolException;
import com.mx.cryptomonitor.portfolio.domain.model.AssetType;
import com.mx.cryptomonitor.portfolio.domain.model.PricePoint;
import com.mx.cryptomonitor.portfolio.infrastructure.outbound.marketdata.BinanceFuturesMarketPriceHistoryAdapter;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

class BinanceFuturesMarketPriceHistoryAdapterTest {

  private static final Instant FIXED_NOW = Instant.parse("2026-01-31T00:00:00Z");
  private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

  private MockWebServer mockWebServer;
  private BinanceFuturesMarketPriceHistoryAdapter adapter;

  @BeforeEach
  void setUp() throws Exception {
    mockWebServer = new MockWebServer();
    mockWebServer.start();
    adapter =
        new BinanceFuturesMarketPriceHistoryAdapter(
            WebClient.builder().baseUrl(mockWebServer.url("/").toString()).build(), FIXED_CLOCK);
  }

  @AfterEach
  void tearDown() throws Exception {
    mockWebServer.shutdown();
  }

  @Test
  void fetchesIndexPriceKlinesUsingPairParam() throws Exception {
    // indexPriceKlines: open-time, open, high, low, close(index), 0(volume), close-time, ...
    long openTimeMs = Instant.parse("2026-01-30T00:00:00Z").toEpochMilli();
    mockWebServer.enqueue(
        json(
            """
            [
              [%d, "84.67", "85.18", "78.59", "80.93", "0", %d, "0", 86400, "0", "0", "0"]
            ]
            """
                .formatted(openTimeMs, openTimeMs + 86_400_000L)));
    mockWebServer.enqueue(json("[]"));

    List<PricePoint> result =
        adapter.fetchPriceHistory(AssetType.CRYPTO, "HYPE", HoldingsHistoryRange.parse("30d"));

    assertThat(result)
        .containsExactly(
            new PricePoint(Instant.parse("2026-01-30T00:00:00Z"), new BigDecimal("80.93")));

    RecordedRequest request = mockWebServer.takeRequest();
    assertThat(request.getRequestUrl().encodedPath()).isEqualTo("/fapi/v1/indexPriceKlines");
    assertThat(request.getRequestUrl().queryParameter("pair")).isEqualTo("HYPEUSDT");
    assertThat(request.getRequestUrl().queryParameter("symbol")).isNull();
  }

  @Test
  void mapsHttp400ToUnknownAssetSymbol() {
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(400)
            .addHeader("Content-Type", "application/json")
            .setBody("{\"code\":-1121,\"msg\":\"Invalid symbol.\"}"));

    assertThatThrownBy(
            () ->
                adapter.fetchPriceHistory(
                    AssetType.CRYPTO, "NOPE", HoldingsHistoryRange.parse("30d")))
        .isInstanceOf(UnknownAssetSymbolException.class);
  }

  @Test
  void supportsOnlyCrypto() {
    assertThat(adapter.supports(AssetType.CRYPTO)).isTrue();
    assertThat(adapter.supports(AssetType.STOCK)).isFalse();
  }

  private MockResponse json(String body) {
    return new MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody(body);
  }
}
