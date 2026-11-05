package com.mx.cryptomonitor.unit.portfolio.infrastructure.outbound.marketdata;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.reactive.function.client.WebClient;

import com.mx.cryptomonitor.asset.application.port.in.AssetCatalogQueryPort;
import com.mx.cryptomonitor.portfolio.domain.exception.MarketDataServerException;
import com.mx.cryptomonitor.portfolio.domain.model.AssetType;
import com.mx.cryptomonitor.portfolio.domain.model.ChartResolution;
import com.mx.cryptomonitor.portfolio.infrastructure.outbound.marketdata.AlphaVantageMarketPriceHistoryAdapter;
import com.mx.cryptomonitor.portfolio.infrastructure.outbound.marketdata.BinanceFuturesMarketPriceHistoryAdapter;
import com.mx.cryptomonitor.portfolio.infrastructure.outbound.marketdata.BinanceMarketPriceHistoryAdapter;
import com.mx.cryptomonitor.portfolio.infrastructure.outbound.marketdata.BybitMarketPriceHistoryAdapter;
import com.mx.cryptomonitor.portfolio.infrastructure.outbound.marketdata.CoinGeckoMarketPriceHistoryAdapter;
import com.mx.cryptomonitor.portfolio.infrastructure.outbound.marketdata.CoinbaseJwtSigner;
import com.mx.cryptomonitor.portfolio.infrastructure.outbound.marketdata.CoinbaseMarketPriceHistoryAdapter;
import com.mx.cryptomonitor.portfolio.infrastructure.outbound.marketdata.EodhdMarketPriceHistoryProvider;
import com.mx.cryptomonitor.portfolio.infrastructure.outbound.marketdata.KrakenMarketPriceHistoryAdapter;
import com.mx.cryptomonitor.portfolio.infrastructure.outbound.marketdata.MarketPriceHistoryProvider;
import com.mx.cryptomonitor.portfolio.infrastructure.outbound.marketdata.MassiveMarketPriceHistoryAdapter;
import com.mx.cryptomonitor.portfolio.infrastructure.outbound.marketdata.TwelveDataMarketPriceHistoryAdapter;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

/**
 * Contrato de resiliencia de geobloqueo para los DIEZ adapters de precios (ADR-0021).
 *
 * <p>Un status de error NO contemplado por los {@code onStatus} específicos de un adapter —sobre
 * todo {@code 451 Unavailable For Legal Reasons} y {@code 403 Forbidden} de un geobloqueo de
 * compliance US, o cualquier otro 4xx como {@code 418}— debe traducirse a una excepción RECUPERABLE
 * ({@link MarketDataServerException}) para que {@code CachedMarketPriceHistoryAdapter} caiga al
 * siguiente proveedor. Sin el catch-all terminal {@code onStatus(HttpStatusCode::isError, ...)}
 * (vía {@code MarketDataResponseErrors}) estos status se filtraban como {@code
 * WebClientResponseException} y la valuación terminaba en HTTP 500.
 *
 * <p>Los diez proveedores comparten el mismo invariante: cualquier bloqueo (403/451) o 4xx
 * inesperado (418) es recuperable. {@code 429} y {@code 404} quedan fuera porque cada adapter los
 * mapea a su propia excepción (rate-limit / símbolo desconocido) y se prueban en sus tests propios.
 */
class MarketDataGeoblockContractTest {

  private static final Clock FIXED_CLOCK =
      Clock.fixed(Instant.parse("2026-01-31T00:00:00Z"), ZoneOffset.UTC);

  private MockWebServer server;
  private PrivateKey ecKey;

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
    ecKey = generateEcPrivateKey();
  }

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
  }

  @ParameterizedTest(name = "Binance spot HTTP {0} -> recuperable")
  @ValueSource(ints = {451, 403, 418})
  void binanceSpot(int status) {
    assertRecoverable(
        status, AssetType.CRYPTO, "BTC", new BinanceMarketPriceHistoryAdapter(wc(), FIXED_CLOCK));
  }

  @ParameterizedTest(name = "Binance futures HTTP {0} -> recuperable")
  @ValueSource(ints = {451, 403, 418})
  void binanceFutures(int status) {
    assertRecoverable(
        status,
        AssetType.CRYPTO,
        "BTC",
        new BinanceFuturesMarketPriceHistoryAdapter(wc(), FIXED_CLOCK));
  }

  @ParameterizedTest(name = "Bybit HTTP {0} -> recuperable")
  @ValueSource(ints = {451, 403, 418})
  void bybit(int status) {
    assertRecoverable(
        status, AssetType.CRYPTO, "BTC", new BybitMarketPriceHistoryAdapter(wc(), FIXED_CLOCK));
  }

  @ParameterizedTest(name = "Kraken HTTP {0} -> recuperable")
  @ValueSource(ints = {451, 403, 418})
  void kraken(int status) {
    assertRecoverable(
        status, AssetType.CRYPTO, "BTC", new KrakenMarketPriceHistoryAdapter(wc(), FIXED_CLOCK));
  }

  @ParameterizedTest(name = "Coinbase HTTP {0} -> recuperable")
  @ValueSource(ints = {451, 403, 418})
  void coinbase(int status) {
    assertRecoverable(
        status,
        AssetType.CRYPTO,
        "BTC",
        new CoinbaseMarketPriceHistoryAdapter(
            wc(), new CoinbaseJwtSigner("test-key", ecKey, Clock.systemUTC()), FIXED_CLOCK));
  }

  @ParameterizedTest(name = "CoinGecko HTTP {0} -> recuperable")
  @ValueSource(ints = {451, 403, 418})
  void coinGecko(int status) {
    AssetCatalogQueryPort catalog = mock(AssetCatalogQueryPort.class);
    when(catalog.findAssetIdBySymbol(anyString())).thenReturn(Optional.of("bitcoin"));
    assertRecoverable(
        status,
        AssetType.CRYPTO,
        "BTC",
        new CoinGeckoMarketPriceHistoryAdapter(
            wc(), true, Duration.ofSeconds(8), catalog, FIXED_CLOCK));
  }

  @ParameterizedTest(name = "AlphaVantage HTTP {0} -> recuperable")
  @ValueSource(ints = {451, 403, 418})
  void alphaVantage(int status) {
    assertRecoverable(
        status,
        AssetType.STOCK,
        "AAPL",
        new AlphaVantageMarketPriceHistoryAdapter(WebClient.builder(), baseUrl(), "test-key"));
  }

  @ParameterizedTest(name = "Massive HTTP {0} -> recuperable")
  @ValueSource(ints = {451, 403, 418})
  void massive(int status) {
    assertRecoverable(
        status,
        AssetType.STOCK,
        "AAPL",
        new MassiveMarketPriceHistoryAdapter(WebClient.builder(), baseUrl(), "test-key"));
  }

  @ParameterizedTest(name = "TwelveData HTTP {0} -> recuperable")
  @ValueSource(ints = {451, 403, 418})
  void twelveData(int status) {
    assertRecoverable(
        status,
        AssetType.STOCK,
        "AAPL",
        new TwelveDataMarketPriceHistoryAdapter(WebClient.builder(), baseUrl(), "test-key"));
  }

  @ParameterizedTest(name = "EODHD historico HTTP {0} -> recuperable")
  @ValueSource(ints = {451, 403, 418})
  void eodhd(int status) {
    assertRecoverable(
        status,
        AssetType.STOCK,
        "AAPL",
        new EodhdMarketPriceHistoryProvider(WebClient.builder(), baseUrl(), "test-token", ".US"));
  }

  private void assertRecoverable(
      int status, AssetType type, String symbol, MarketPriceHistoryProvider adapter) {
    server.enqueue(new MockResponse().setResponseCode(status).setBody("blocked"));
    assertThatThrownBy(() -> adapter.fetchPriceHistory(type, symbol, dailyWindow()))
        .isInstanceOf(MarketDataServerException.class);
  }

  private WebClient wc() {
    return WebClient.builder().baseUrl(baseUrl()).build();
  }

  private String baseUrl() {
    return server.url("/").toString();
  }

  private ChartResolution dailyWindow() {
    return new ChartResolution(
        Instant.parse("2026-01-01T00:00:00Z"),
        Instant.parse("2026-01-02T00:00:00Z"),
        Duration.ofDays(1),
        "1d",
        1);
  }

  private static PrivateKey generateEcPrivateKey() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
    generator.initialize(new ECGenParameterSpec("secp256r1"));
    KeyPair keyPair = generator.generateKeyPair();
    return keyPair.getPrivate();
  }
}
