package com.mx.cryptomonitor.unit.portfolio.infrastructure.outbound.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
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
import com.mx.cryptomonitor.portfolio.infrastructure.outbound.marketdata.CoinbaseJwtSigner;
import com.mx.cryptomonitor.portfolio.infrastructure.outbound.marketdata.CoinbaseMarketPriceHistoryAdapter;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

class CoinbaseMarketPriceHistoryAdapterTest {

  private MockWebServer mockWebServer;
  private CoinbaseJwtSigner jwtSigner;
  private CoinbaseMarketPriceHistoryAdapter adapter;

  @BeforeEach
  void setUp() throws Exception {
    mockWebServer = new MockWebServer();
    mockWebServer.start();
    jwtSigner = new CoinbaseJwtSigner("test-key", generateEcPrivateKey(), Clock.systemUTC());
    adapter =
        new CoinbaseMarketPriceHistoryAdapter(
            WebClient.builder().baseUrl(mockWebServer.url("/").toString()).build(),
            jwtSigner,
            Clock.systemUTC());
  }

  @AfterEach
  void tearDown() throws Exception {
    mockWebServer.shutdown();
  }

  @Test
  void mapsDailyCandleCloseAndSignsRequest() throws Exception {
    long startSeconds = Instant.parse("2026-01-01T00:00:00Z").getEpochSecond();
    mockWebServer.enqueue(
        json(
            """
            {"candles":[{"start":"%d","low":"49000.00","high":"51000.00","open":"50000.00","close":"50500.00","volume":"1.5"}]}
            """
                .formatted(startSeconds)));

    List<PricePoint> result = adapter.fetchPriceHistory(AssetType.CRYPTO, "BTC", dailyOneDay());

    assertThat(result)
        .containsExactly(
            new PricePoint(Instant.parse("2026-01-01T00:00:00Z"), new BigDecimal("50500.00")));

    RecordedRequest request = mockWebServer.takeRequest();
    assertThat(request.getRequestUrl().encodedPath())
        .isEqualTo("/api/v3/brokerage/products/BTC-USD/candles");
    assertThat(request.getRequestUrl().queryParameter("granularity")).isEqualTo("ONE_DAY");
    assertThat(request.getHeader("Authorization")).startsWith("Bearer eyJ");
  }

  @Test
  void unknownProductIsMappedToRecoverableExceptionForFallback() {
    mockWebServer.enqueue(
        new MockResponse().setResponseCode(404).setBody("{\"error\":\"NotFound\"}"));

    assertThatThrownBy(() -> adapter.fetchPriceHistory(AssetType.CRYPTO, "NOPE", dailyOneDay()))
        .isInstanceOf(UnknownAssetSymbolException.class);
  }

  @Test
  void disabledAdapterSignalsFallback() {
    CoinbaseMarketPriceHistoryAdapter disabled =
        new CoinbaseMarketPriceHistoryAdapter(
            WebClient.builder().baseUrl(mockWebServer.url("/").toString()).build(),
            jwtSigner,
            false,
            Duration.ofSeconds(10),
            Clock.systemUTC());

    assertThatThrownBy(() -> disabled.fetchPriceHistory(AssetType.CRYPTO, "BTC", dailyOneDay()))
        .isInstanceOf(UnknownAssetSymbolException.class);
  }

  @Test
  void missingCdpCredentialSignalsFallback() {
    CoinbaseJwtSigner unconfigured =
        new CoinbaseJwtSigner("", (java.security.PrivateKey) null, Clock.systemUTC());
    CoinbaseMarketPriceHistoryAdapter withoutCredential =
        new CoinbaseMarketPriceHistoryAdapter(
            WebClient.builder().baseUrl(mockWebServer.url("/").toString()).build(),
            unconfigured,
            Clock.systemUTC());

    assertThatThrownBy(
            () -> withoutCredential.fetchPriceHistory(AssetType.CRYPTO, "BTC", dailyOneDay()))
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

  private static java.security.PrivateKey generateEcPrivateKey() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
    generator.initialize(new ECGenParameterSpec("secp256r1"));
    KeyPair keyPair = generator.generateKeyPair();
    return keyPair.getPrivate();
  }

  private MockResponse json(String body) {
    return new MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody(body);
  }
}
