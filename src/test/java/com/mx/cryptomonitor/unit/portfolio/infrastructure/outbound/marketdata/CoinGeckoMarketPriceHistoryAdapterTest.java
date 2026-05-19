package com.mx.cryptomonitor.unit.portfolio.infrastructure.outbound.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import com.mx.cryptomonitor.asset.application.port.in.AssetCatalogQueryPort;
import com.mx.cryptomonitor.portfolio.application.service.HoldingsHistoryRange;
import com.mx.cryptomonitor.portfolio.domain.model.AssetType;
import com.mx.cryptomonitor.portfolio.domain.model.PricePoint;
import com.mx.cryptomonitor.portfolio.infrastructure.outbound.marketdata.CoinGeckoMarketPriceHistoryAdapter;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

class CoinGeckoMarketPriceHistoryAdapterTest {

  // Fixed "now" so all time-range assertions are deterministic
  private static final Instant FIXED_NOW = Instant.parse("2026-01-31T00:00:00Z");
  private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

  private MockWebServer mockWebServer;
  private AssetCatalogQueryPort assetCatalogQueryPort;
  private CoinGeckoMarketPriceHistoryAdapter adapter;

  @BeforeEach
  void setUp() throws Exception {
    mockWebServer = new MockWebServer();
    mockWebServer.start();
    assetCatalogQueryPort = org.mockito.Mockito.mock(AssetCatalogQueryPort.class);
    adapter =
        new CoinGeckoMarketPriceHistoryAdapter(
            WebClient.builder().baseUrl(mockWebServer.url("/").toString()).build(),
            true,
            Duration.ofSeconds(2),
            assetCatalogQueryPort,
            FIXED_CLOCK);
  }

  @AfterEach
  void tearDown() throws Exception {
    mockWebServer.shutdown();
  }

  @Test
  void fetchPriceHistoryResolvesUnknownCatalogSymbolThroughCoinGeckoSearch() throws Exception {
    when(assetCatalogQueryPort.findAssetIdBySymbol("HYPE")).thenReturn(Optional.empty());
    mockWebServer.enqueue(
        json(
            """
            {
              "coins": [
                { "id": "small-hype", "name": "Small Hype", "symbol": "HYPE", "market_cap_rank": 500 },
                { "id": "hyperliquid", "name": "Hyperliquid", "symbol": "HYPE", "market_cap_rank": 11 }
              ]
            }
            """));
    mockWebServer.enqueue(
        json(
            """
            {
              "prices": [
                [1767225600000, 21.47]
              ]
            }
            """));

    java.util.List<PricePoint> result =
        adapter.fetchPriceHistory(AssetType.CRYPTO, "HYPE", HoldingsHistoryRange.parse("30d"));

    assertThat(result)
        .containsExactly(
            new PricePoint(
                java.time.Instant.parse("2026-01-01T00:00:00Z"), new BigDecimal("21.47")));

    RecordedRequest searchRequest = mockWebServer.takeRequest();
    assertThat(searchRequest.getRequestUrl().encodedPath()).isEqualTo("/search");
    assertThat(searchRequest.getRequestUrl().queryParameter("query")).isEqualTo("HYPE");

    RecordedRequest chartRequest = mockWebServer.takeRequest();
    assertThat(chartRequest.getRequestUrl().encodedPath())
        .isEqualTo("/coins/hyperliquid/market_chart/range");
    assertThat(chartRequest.getRequestUrl().queryParameter("vs_currency")).isEqualTo("usd");
    long from = Long.parseLong(chartRequest.getRequestUrl().queryParameter("from"));
    long to = Long.parseLong(chartRequest.getRequestUrl().queryParameter("to"));
    // With Clock.fixed at 2026-01-31T00:00:00Z and range=30d:
    //   end   = 2026-01-31T00:00:00Z → epoch 1738281600
    //   start = 2026-01-01T00:00:00Z → epoch 1735689600
    assertThat(to).isEqualTo(FIXED_NOW.getEpochSecond());
    assertThat(from).isEqualTo(FIXED_NOW.minus(Duration.ofDays(30)).getEpochSecond());
  }

  private MockResponse json(String body) {
    return new MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody(body);
  }
}
