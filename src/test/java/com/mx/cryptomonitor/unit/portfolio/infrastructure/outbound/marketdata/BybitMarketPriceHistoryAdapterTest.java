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
import com.mx.cryptomonitor.portfolio.infrastructure.outbound.marketdata.BybitMarketPriceHistoryAdapter;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

class BybitMarketPriceHistoryAdapterTest {

  private static final Instant FIXED_NOW = Instant.parse("2026-01-31T00:00:00Z");
  private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

  private MockWebServer mockWebServer;
  private BybitMarketPriceHistoryAdapter adapter;

  @BeforeEach
  void setUp() throws Exception {
    mockWebServer = new MockWebServer();
    mockWebServer.start();
    adapter =
        new BybitMarketPriceHistoryAdapter(
            WebClient.builder().baseUrl(mockWebServer.url("/").toString()).build(), FIXED_CLOCK);
  }

  @AfterEach
  void tearDown() throws Exception {
    mockWebServer.shutdown();
  }

  @Test
  void fetchesSpotKlinesAndSortsNewestFirstListAscending() throws Exception {
    long day30 = Instant.parse("2026-01-30T00:00:00Z").toEpochMilli();
    long day29 = Instant.parse("2026-01-29T00:00:00Z").toEpochMilli();
    // Bybit devuelve nuevo->antiguo: la vela del dia 30 va primero.
    mockWebServer.enqueue(
        json(
            """
            {"retCode":0,"retMsg":"OK","result":{"symbol":"HYPEUSDT","category":"spot","list":[
              ["%d","80.00","83.00","79.00","82.00","100.0","8200.0"],
              ["%d","78.00","81.00","77.00","80.00","120.0","9600.0"]
            ]}}
            """
                .formatted(day30, day29)));

    List<PricePoint> result =
        adapter.fetchPriceHistory(AssetType.CRYPTO, "HYPE", HoldingsHistoryRange.parse("1y"));

    assertThat(result)
        .containsExactly(
            new PricePoint(Instant.parse("2026-01-29T00:00:00Z"), new BigDecimal("80.00")),
            new PricePoint(Instant.parse("2026-01-30T00:00:00Z"), new BigDecimal("82.00")));

    RecordedRequest request = mockWebServer.takeRequest();
    assertThat(request.getRequestUrl().encodedPath()).isEqualTo("/v5/market/kline");
    assertThat(request.getRequestUrl().queryParameter("category")).isEqualTo("spot");
    assertThat(request.getRequestUrl().queryParameter("symbol")).isEqualTo("HYPEUSDT");
    // range 1y -> ~365 dias -> resolucion diaria -> intervalo Bybit "D"
    assertThat(request.getRequestUrl().queryParameter("interval")).isEqualTo("D");
  }

  @Test
  void mapsRetCode10001ToUnknownAssetSymbol() {
    mockWebServer.enqueue(
        json("{\"retCode\":10001,\"retMsg\":\"Not supported symbols\",\"result\":{}}"));

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
