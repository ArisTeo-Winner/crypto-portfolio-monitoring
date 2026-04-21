package com.mx.cryptomonitor.integration.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.web.reactive.function.client.WebClient;

import com.mx.cryptomonitor.marketdata.application.port.out.CryptoHistoricalPriceSeries;
import com.mx.cryptomonitor.marketdata.domain.exception.CoinGeckoInvalidAssetException;
import com.mx.cryptomonitor.marketdata.domain.exception.CoinGeckoRateLimitException;
import com.mx.cryptomonitor.marketdata.infrastructure.configuration.CoinGeckoProperties;
import com.mx.cryptomonitor.marketdata.infrastructure.outbound.coingecko.CoinGeckoHistoricalPriceAdapter;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

class CoinGeckoHistoricalPriceAdapterTest {

  private MockWebServer mockWebServer;
  private CoinGeckoHistoricalPriceAdapter adapter;
  private ConcurrentMapCacheManager cacheManager;

  @BeforeEach
  void setUp() throws Exception {
    mockWebServer = new MockWebServer();
    mockWebServer.start();
    cacheManager = new ConcurrentMapCacheManager(CoinGeckoHistoricalPriceAdapter.CACHE_NAME);

    CoinGeckoProperties properties =
        new CoinGeckoProperties(
            mockWebServer.url("/").toString(),
            null,
            Duration.ofSeconds(5),
            Duration.ofMinutes(15),
            true);
    adapter =
        new CoinGeckoHistoricalPriceAdapter(
            WebClient.builder().baseUrl(properties.baseUrl()).build(), properties, cacheManager);
  }

  @AfterEach
  void tearDown() throws Exception {
    mockWebServer.shutdown();
  }

  @Test
  void getHistoricalUsdPrices_returnsMappedSeries_whenApiReturnsPricePoints() throws Exception {
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody(
                """
                {
                  "prices": [
                    [1743984000000, 743.13],
                    [1744070400000, 812.42]
                  ]
                }
                """));

    Instant from = Instant.parse("2025-04-07T00:00:00Z");
    Instant to = Instant.parse("2025-04-08T00:00:00Z");

    var result = adapter.getHistoricalUsdPrices("bitcoin", from, to).block();

    assertThat(result).isNotNull();
    assertThat(result.assetId()).isEqualTo("bitcoin");
    assertThat(result.vsCurrency()).isEqualTo("usd");
    assertThat(result.points()).hasSize(2);
    assertThat(result.points().get(0).timestamp()).isEqualTo(Instant.ofEpochMilli(1743984000000L));
    assertThat(result.points().get(0).priceUsd()).isEqualByComparingTo("743.13");
    assertThat(result.points().get(1).priceUsd()).isEqualByComparingTo("812.42");

    RecordedRequest request = mockWebServer.takeRequest();
    assertThat(request.getMethod()).isEqualTo("GET");
    assertThat(request.getRequestUrl().encodedPath())
        .isEqualTo("/coins/bitcoin/market_chart/range");
    assertThat(request.getRequestUrl().queryParameter("vs_currency")).isEqualTo("usd");
    assertThat(request.getRequestUrl().queryParameter("from"))
        .isEqualTo(String.valueOf(from.getEpochSecond()));
    assertThat(request.getRequestUrl().queryParameter("to"))
        .isEqualTo(String.valueOf(to.getEpochSecond()));
  }

  @Test
  void getHistoricalUsdPrices_throwsRateLimitException_whenApiReturns429() {
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(429)
            .addHeader("Content-Type", "application/json")
            .setBody("{\"status\":{\"error_message\":\"rate limit\"}}"));

    assertThatThrownBy(
            () ->
                adapter
                    .getHistoricalUsdPrices(
                        "bitcoin",
                        Instant.parse("2025-04-07T00:00:00Z"),
                        Instant.parse("2025-04-08T00:00:00Z"))
                    .block())
        .isInstanceOf(CoinGeckoRateLimitException.class)
        .hasMessageContaining("rate limit");
  }

  @Test
  void getHistoricalUsdPrices_throwsInvalidAssetException_whenApiReturns404() {
    mockWebServer.enqueue(new MockResponse().setResponseCode(404).setBody("{}"));

    assertThatThrownBy(
            () ->
                adapter
                    .getHistoricalUsdPrices(
                        "missing-coin",
                        Instant.parse("2025-04-07T00:00:00Z"),
                        Instant.parse("2025-04-08T00:00:00Z"))
                    .block())
        .isInstanceOf(CoinGeckoInvalidAssetException.class)
        .hasMessageContaining("missing-coin");
  }

  @Test
  void getHistoricalUsdPrices_returnsCachedSeries_whenSameRequestIsRepeated() {
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody(
                """
                {
                  "prices": [
                    [1743984000000, 743.13],
                    [1744070400000, 812.42]
                  ]
                }
                """));

    Instant from = Instant.parse("2025-04-07T00:00:00Z");
    Instant to = Instant.parse("2025-04-08T00:00:00Z");

    CryptoHistoricalPriceSeries first = adapter.getHistoricalUsdPrices("bitcoin", from, to).block();
    CryptoHistoricalPriceSeries second =
        adapter.getHistoricalUsdPrices("bitcoin", from, to).block();

    assertThat(first).isEqualTo(second);
    assertThat(mockWebServer.getRequestCount()).isEqualTo(1);
  }

  @Test
  void getHistoricalUsdPricesByDays_returnsMappedSeries_whenMarketChartApiReturnsPricePoints()
      throws Exception {
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody(
                """
                {
                  "prices": [
                    [1743984000000, 743.13],
                    [1744070400000, 812.42]
                  ]
                }
                """));

    var result = adapter.getHistoricalUsdPrices("bitcoin", 30).block();

    assertThat(result).isNotNull();
    assertThat(result.assetId()).isEqualTo("bitcoin");
    assertThat(result.vsCurrency()).isEqualTo("usd");
    assertThat(result.points()).hasSize(2);
    assertThat(result.points().get(0).priceUsd()).isEqualByComparingTo("743.13");
    assertThat(result.points().get(1).priceUsd()).isEqualByComparingTo("812.42");

    RecordedRequest request = mockWebServer.takeRequest();
    assertThat(request.getMethod()).isEqualTo("GET");
    assertThat(request.getRequestUrl().encodedPath()).isEqualTo("/coins/bitcoin/market_chart");
    assertThat(request.getRequestUrl().queryParameter("vs_currency")).isEqualTo("usd");
    assertThat(request.getRequestUrl().queryParameter("days")).isEqualTo("30");
  }

  @Test
  void getHistoricalUsdPricesByDays_throwsRateLimitException_whenMarketChartApiReturns429() {
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(429)
            .addHeader("Content-Type", "application/json")
            .setBody("{\"status\":{\"error_message\":\"rate limit\"}}"));

    assertThatThrownBy(() -> adapter.getHistoricalUsdPrices("bitcoin", 30).block())
        .isInstanceOf(CoinGeckoRateLimitException.class)
        .hasMessageContaining("rate limit");
  }

  @Test
  void getHistoricalUsdPricesByDays_returnsCachedSeries_whenSameRequestIsRepeated() {
    mockWebServer.enqueue(
        new MockResponse()
            .setResponseCode(200)
            .addHeader("Content-Type", "application/json")
            .setBody(
                """
                {
                  "prices": [
                    [1743984000000, 743.13],
                    [1744070400000, 812.42]
                  ]
                }
                """));

    CryptoHistoricalPriceSeries first = adapter.getHistoricalUsdPrices("bitcoin", 30).block();
    CryptoHistoricalPriceSeries second = adapter.getHistoricalUsdPrices("bitcoin", 30).block();

    assertThat(first).isEqualTo(second);
    assertThat(mockWebServer.getRequestCount()).isEqualTo(1);
  }
}
