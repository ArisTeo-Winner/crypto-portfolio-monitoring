package com.mx.cryptomonitor.unit.portfolio.infrastructure.outbound.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import com.mx.cryptomonitor.portfolio.application.service.HoldingsHistoryRange;
import com.mx.cryptomonitor.portfolio.domain.exception.MarketDataRateLimitException;
import com.mx.cryptomonitor.portfolio.domain.exception.MarketDataServerException;
import com.mx.cryptomonitor.portfolio.domain.exception.UnknownAssetSymbolException;
import com.mx.cryptomonitor.portfolio.domain.model.AssetType;
import com.mx.cryptomonitor.portfolio.domain.model.ChartResolution;
import com.mx.cryptomonitor.portfolio.domain.model.PricePoint;
import com.mx.cryptomonitor.portfolio.infrastructure.outbound.marketdata.TwelveDataMarketPriceHistoryAdapter;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;

/**
 * Tests unitarios de {@link TwelveDataMarketPriceHistoryAdapter}.
 *
 * <p>Agrupa escenarios por categoría: soporte de tipos, respuestas exitosas (diario e intraday),
 * mapeo de intervalos, errores en body (HTTP 200 + status error), errores HTTP, fallos de red y
 * parámetros de la request.
 */
class TwelveDataMarketPriceHistoryAdapterTest {

  private MockWebServer server;
  private TwelveDataMarketPriceHistoryAdapter adapter;

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
    adapter =
        new TwelveDataMarketPriceHistoryAdapter(
            WebClient.builder(), server.url("/").toString(), "test-api-key");
  }

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
  }

  // -------------------------------------------------------------------------
  // supports()
  // -------------------------------------------------------------------------

  @Test
  void shouldSupportStockEtfAndIndex() {
    assertThat(adapter.supports(AssetType.STOCK)).isTrue();
    assertThat(adapter.supports(AssetType.ETF)).isTrue();
    assertThat(adapter.supports(AssetType.INDEX)).isTrue();
  }

  @Test
  void shouldNotSupportCrypto() {
    assertThat(adapter.supports(AssetType.CRYPTO)).isFalse();
  }

  // -------------------------------------------------------------------------
  // Respuestas exitosas — serie diaria
  // -------------------------------------------------------------------------

  @Test
  void shouldParseDailySeriesInAscendingOrder() {
    server.enqueue(
        jsonOk(
            """
        {
          "meta": {"symbol": "AAPL", "interval": "1day", "exchange_timezone": "America/New_York"},
          "values": [
            {"datetime": "2026-05-22", "close": "309.50", "volume": "5000000"},
            {"datetime": "2026-05-21", "close": "307.80", "volume": "4500000"},
            {"datetime": "2026-05-20", "close": "306.10", "volume": "4000000"}
          ],
          "status": "ok"
        }
        """));

    List<PricePoint> points =
        adapter.fetchPriceHistory(AssetType.STOCK, "AAPL", HoldingsHistoryRange.parse("30d"));

    assertThat(points).hasSize(3);
    assertThat(points.get(0).price()).isEqualByComparingTo(new BigDecimal("306.10"));
    assertThat(points.get(1).price()).isEqualByComparingTo(new BigDecimal("307.80"));
    assertThat(points.get(2).price()).isEqualByComparingTo(new BigDecimal("309.50"));
    // Garantizar orden temporal ascendente
    assertThat(points.get(0).time()).isBefore(points.get(1).time());
    assertThat(points.get(1).time()).isBefore(points.get(2).time());
  }

  @Test
  void shouldParseDailySeriesWithExactPriceValues() {
    server.enqueue(
        jsonOk(
            """
        {
          "meta": {"symbol": "MSFT", "interval": "1day"},
          "values": [
            {"datetime": "2026-05-22", "close": "425.123456789"}
          ],
          "status": "ok"
        }
        """));

    List<PricePoint> points =
        adapter.fetchPriceHistory(AssetType.ETF, "SPY", HoldingsHistoryRange.parse("7d"));

    assertThat(points).hasSize(1);
    assertThat(points.get(0).price()).isEqualByComparingTo("425.123456789");
  }

  // -------------------------------------------------------------------------
  // Respuestas exitosas — serie intraday
  // -------------------------------------------------------------------------

  @Test
  void shouldParseIntradaySeriesUsingExchangeTimezone() {
    server.enqueue(
        jsonOk(
            """
        {
          "meta": {
            "symbol": "AAPL",
            "interval": "1h",
            "exchange_timezone": "America/New_York"
          },
          "values": [
            {"datetime": "2026-05-22 15:30:00", "close": "308.88", "volume": "4183601"},
            {"datetime": "2026-05-22 14:30:00", "close": "308.70", "volume": "2681340"},
            {"datetime": "2026-05-22 13:30:00", "close": "309.31", "volume": "2598845"}
          ],
          "status": "ok"
        }
        """));

    List<PricePoint> points =
        adapter.fetchPriceHistory(AssetType.STOCK, "AAPL", HoldingsHistoryRange.parse("24h"));

    assertThat(points).hasSize(3);
    // Orden ascendente: 13:30 < 14:30 < 15:30
    assertThat(points.get(0).price()).isEqualByComparingTo("309.31");
    assertThat(points.get(1).price()).isEqualByComparingTo("308.70");
    assertThat(points.get(2).price()).isEqualByComparingTo("308.88");
    assertThat(points.get(0).time()).isBefore(points.get(1).time());
    assertThat(points.get(1).time()).isBefore(points.get(2).time());
  }

  @Test
  void shouldFallbackToDefaultTimezoneWhenMetaIsMissing() {
    server.enqueue(
        jsonOk(
            """
        {
          "meta": {},
          "values": [
            {"datetime": "2026-05-22 15:30:00", "close": "308.88"}
          ],
          "status": "ok"
        }
        """));

    List<PricePoint> points =
        adapter.fetchPriceHistory(AssetType.STOCK, "AAPL", HoldingsHistoryRange.parse("24h"));

    assertThat(points).hasSize(1);
    assertThat(points.get(0).price()).isEqualByComparingTo("308.88");
  }

  // -------------------------------------------------------------------------
  // Mapeo de intervalos
  // -------------------------------------------------------------------------

  @Test
  void shouldUseInterval1dayForDailyRanges() throws Exception {
    for (String range : List.of("7d", "30d", "90d", "180d", "1y")) {
      server.enqueue(
          jsonOk("""
          {"meta": {}, "values": [], "status": "ok"}
          """));
      adapter.fetchPriceHistory(AssetType.STOCK, "AAPL", HoldingsHistoryRange.parse(range));
      RecordedRequest req = server.takeRequest(1, TimeUnit.SECONDS);
      assertThat(req.getPath()).as("range=%s", range).contains("interval=1day");
    }
  }

  @Test
  void shouldUseInterval1hForIntradayRange() throws Exception {
    server.enqueue(jsonOk("""
        {"meta": {}, "values": [], "status": "ok"}
        """));

    adapter.fetchPriceHistory(AssetType.STOCK, "AAPL", HoldingsHistoryRange.parse("24h"));

    RecordedRequest req = server.takeRequest(1, TimeUnit.SECONDS);
    assertThat(req.getPath()).contains("interval=1h");
  }

  @Test
  void shouldUseInterval1dayForAllRange() throws Exception {
    server.enqueue(jsonOk("""
        {"meta": {}, "values": [], "status": "ok"}
        """));

    adapter.fetchPriceHistory(AssetType.STOCK, "AAPL", HoldingsHistoryRange.parse("all"));

    RecordedRequest req = server.takeRequest(1, TimeUnit.SECONDS);
    assertThat(req.getPath()).contains("interval=1day").contains("outputsize=5000");
  }

  @Test
  void shouldDeriveIntervalFromDurationRegardlessOfProviderIntervalCode() throws Exception {
    server.enqueue(jsonOk("""
        {"meta": {}, "values": [], "status": "ok"}
        """));

    ChartResolution chart =
        new ChartResolution(
            Instant.parse("2026-05-15T00:00:00Z"),
            Instant.parse("2026-05-22T00:00:00Z"),
            Duration.ofHours(4),
            "4h",
            42);

    adapter.fetchPriceHistory(AssetType.STOCK, "AAPL", chart);

    RecordedRequest req = server.takeRequest(1, TimeUnit.SECONDS);
    assertThat(req.getPath()).contains("interval=4h");
  }

  @Test
  void shouldFallbackToIntervalFromDurationWhenProviderCodeIsBlank() throws Exception {
    server.enqueue(jsonOk("""
        {"meta": {}, "values": [], "status": "ok"}
        """));

    ChartResolution chart =
        new ChartResolution(
            Instant.parse("2026-05-15T00:00:00Z"),
            Instant.parse("2026-05-22T00:00:00Z"),
            Duration.ofMinutes(30),
            "", // sin providerIntervalCode
            42);

    adapter.fetchPriceHistory(AssetType.STOCK, "AAPL", chart);

    RecordedRequest req = server.takeRequest(1, TimeUnit.SECONDS);
    assertThat(req.getPath()).contains("interval=30min");
  }

  /**
   * Regresion del incidente: para rangos cortos (24h/7d), {@link
   * com.mx.cryptomonitor.portfolio.domain.model.ChartResolutionStrategy} genera codigos
   * Binance-style ("5m", "15m") en {@code providerIntervalCode}. Twelve Data solo acepta "5min",
   * "15min", etc., y respondia HTTP 400 ("Invalid interval provided: 5m") cuando este adaptador
   * reenviaba ese codigo tal cual, lo que el controlador convertia en un 502 que dejaba la grafica
   * de portfolio en blanco para 24h/7d.
   */
  @Test
  void shouldTranslateBinanceStyleProviderIntervalCodeToTwelveDataFormat() throws Exception {
    server.enqueue(jsonOk("""
        {"meta": {}, "values": [], "status": "ok"}
        """));

    ChartResolution chart =
        new ChartResolution(
            Instant.now().minus(Duration.ofHours(24)),
            Instant.now(),
            Duration.ofMinutes(5),
            "5m", // codigo Binance-style, invalido para Twelve Data
            288);

    adapter.fetchPriceHistory(AssetType.STOCK, "AAPL", chart);

    RecordedRequest req = server.takeRequest(1, TimeUnit.SECONDS);
    assertThat(req.getPath())
        .as("debe traducir '5m' (Binance) a '5min' (Twelve Data), nunca reenviar '5m'")
        .contains("interval=5min&")
        .doesNotContain("interval=5m&");
  }

  @Test
  void shouldTranslateDayOrLongerChartResolutionIntervalTo1day() throws Exception {
    server.enqueue(jsonOk("""
        {"meta": {}, "values": [], "status": "ok"}
        """));

    ChartResolution chart =
        new ChartResolution(
            Instant.parse("2024-01-01T00:00:00Z"),
            Instant.parse("2026-05-22T00:00:00Z"),
            Duration.ofDays(1),
            "1d", // codigo Binance-style, invalido para Twelve Data
            900);

    adapter.fetchPriceHistory(AssetType.STOCK, "AAPL", chart);

    RecordedRequest req = server.takeRequest(1, TimeUnit.SECONDS);
    assertThat(req.getPath()).contains("interval=1day&").doesNotContain("interval=1d&");
  }

  // -------------------------------------------------------------------------
  // Errores en body con HTTP 200 (status:"error")
  // -------------------------------------------------------------------------

  @Test
  void shouldThrowRateLimitExceptionWhenBodyCode429() {
    server.enqueue(
        jsonOk(
            """
        {
          "code": 429,
          "message": "You have run out of API credits for the current minute.",
          "status": "error"
        }
        """));

    assertThatThrownBy(
            () ->
                adapter.fetchPriceHistory(
                    AssetType.STOCK, "AAPL", HoldingsHistoryRange.parse("30d")))
        .isInstanceOf(MarketDataRateLimitException.class)
        .hasMessageContaining("rate limit");
  }

  @Test
  void shouldThrowUnknownAssetSymbolExceptionWhenBodyCode400AndSymbolNotValid() {
    // "**INVALIDO** is not valid." → símbolo inexistente → detener fallback
    server.enqueue(
        jsonOk(
            """
        {
          "code": 400,
          "message": "**INVALIDO** is not valid.",
          "status": "error"
        }
        """));

    assertThatThrownBy(
            () ->
                adapter.fetchPriceHistory(
                    AssetType.STOCK, "INVALIDO", HoldingsHistoryRange.parse("30d")))
        .isInstanceOf(UnknownAssetSymbolException.class);
  }

  @Test
  void shouldThrowMarketDataServerExceptionWhenBodyCode400AndNoDataForDates() {
    // "No data is available on the specified dates" → símbolo válido, problema de rango
    // → debe permitir fallback al siguiente proveedor (Polygon → AlphaVantage)
    server.enqueue(
        jsonOk(
            """
        {
          "code": 400,
          "message": "No data is available on the specified dates. Try setting different start/end dates.",
          "status": "error"
        }
        """));

    assertThatThrownBy(
            () ->
                adapter.fetchPriceHistory(
                    AssetType.STOCK, "IBM", HoldingsHistoryRange.parse("30d")))
        .isInstanceOf(MarketDataServerException.class)
        // NO debe ser UnknownAssetSymbolException — eso detendría el fallback
        .isNotInstanceOf(UnknownAssetSymbolException.class);
  }

  @Test
  void shouldThrowUnknownAssetSymbolExceptionWhenBodyCode404() {
    server.enqueue(
        jsonOk(
            """
        {
          "code": 404,
          "message": "Symbol not found.",
          "status": "error"
        }
        """));

    assertThatThrownBy(
            () ->
                adapter.fetchPriceHistory(
                    AssetType.STOCK, "XXXXX", HoldingsHistoryRange.parse("30d")))
        .isInstanceOf(UnknownAssetSymbolException.class);
  }

  @Test
  void shouldThrowMarketDataServerExceptionWhenBodyCode401ApiKeyInvalid() {
    server.enqueue(
        jsonOk(
            """
        {
          "code": 401,
          "message": "Your API key is invalid.",
          "status": "error"
        }
        """));

    assertThatThrownBy(
            () ->
                adapter.fetchPriceHistory(
                    AssetType.STOCK, "AAPL", HoldingsHistoryRange.parse("30d")))
        .isInstanceOf(MarketDataServerException.class)
        .hasMessageContaining("invalida o expirada");
  }

  @Test
  void shouldThrowMarketDataServerExceptionWhenBodyCode403ApiKeyExpired() {
    server.enqueue(
        jsonOk(
            """
        {
          "code": 403,
          "message": "Your API key does not have access to this resource.",
          "status": "error"
        }
        """));

    assertThatThrownBy(
            () ->
                adapter.fetchPriceHistory(
                    AssetType.STOCK, "AAPL", HoldingsHistoryRange.parse("30d")))
        .isInstanceOf(MarketDataServerException.class)
        .hasMessageContaining("invalida o expirada");
  }

  @Test
  void shouldThrowMarketDataServerExceptionForUnknownBodyErrorCode() {
    server.enqueue(
        jsonOk(
            """
        {
          "code": 503,
          "message": "Service temporarily unavailable.",
          "status": "error"
        }
        """));

    assertThatThrownBy(
            () ->
                adapter.fetchPriceHistory(
                    AssetType.STOCK, "AAPL", HoldingsHistoryRange.parse("30d")))
        .isInstanceOf(MarketDataServerException.class);
  }

  // -------------------------------------------------------------------------
  // Errores HTTP
  // -------------------------------------------------------------------------

  @Test
  void shouldThrowMarketDataServerExceptionOnHttp401ApiKeyInvalid() {
    server.enqueue(new MockResponse().setResponseCode(401).setBody("Unauthorized"));

    assertThatThrownBy(
            () ->
                adapter.fetchPriceHistory(
                    AssetType.STOCK, "AAPL", HoldingsHistoryRange.parse("30d")))
        .isInstanceOf(MarketDataServerException.class)
        .hasMessageContaining("invalida o expirada")
        .hasMessageContaining("401");
  }

  @Test
  void shouldThrowMarketDataServerExceptionOnHttp403ApiKeyForbidden() {
    server.enqueue(new MockResponse().setResponseCode(403).setBody("Forbidden"));

    assertThatThrownBy(
            () ->
                adapter.fetchPriceHistory(
                    AssetType.STOCK, "AAPL", HoldingsHistoryRange.parse("30d")))
        .isInstanceOf(MarketDataServerException.class)
        .hasMessageContaining("invalida o expirada")
        .hasMessageContaining("403");
  }

  @Test
  void shouldThrowMarketDataRateLimitExceptionOnHttp429() {
    server.enqueue(new MockResponse().setResponseCode(429).setBody("Too Many Requests"));

    assertThatThrownBy(
            () ->
                adapter.fetchPriceHistory(
                    AssetType.STOCK, "AAPL", HoldingsHistoryRange.parse("30d")))
        .isInstanceOf(MarketDataRateLimitException.class);
  }

  @Test
  void shouldThrowUnknownAssetSymbolExceptionOnHttp404() {
    server.enqueue(new MockResponse().setResponseCode(404));

    assertThatThrownBy(
            () ->
                adapter.fetchPriceHistory(
                    AssetType.STOCK, "INVALIDO", HoldingsHistoryRange.parse("30d")))
        .isInstanceOf(UnknownAssetSymbolException.class);
  }

  @Test
  void shouldThrowMarketDataServerExceptionOnHttp500() {
    server.enqueue(new MockResponse().setResponseCode(500).setBody("Internal Server Error"));

    assertThatThrownBy(
            () ->
                adapter.fetchPriceHistory(
                    AssetType.STOCK, "AAPL", HoldingsHistoryRange.parse("30d")))
        .isInstanceOf(MarketDataServerException.class);
  }

  @Test
  void shouldThrowMarketDataServerExceptionOnHttp503() {
    server.enqueue(new MockResponse().setResponseCode(503));

    assertThatThrownBy(
            () ->
                adapter.fetchPriceHistory(
                    AssetType.STOCK, "AAPL", HoldingsHistoryRange.parse("30d")))
        .isInstanceOf(MarketDataServerException.class);
  }

  // -------------------------------------------------------------------------
  // Errores de red / timeout
  // -------------------------------------------------------------------------

  @Test
  void shouldThrowMarketDataServerExceptionOnNetworkDisconnect() {
    server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));

    assertThatThrownBy(
            () ->
                adapter.fetchPriceHistory(
                    AssetType.STOCK, "AAPL", HoldingsHistoryRange.parse("30d")))
        .isInstanceOf(MarketDataServerException.class);
  }

  // -------------------------------------------------------------------------
  // Respuesta vacía o con datos malformados
  // -------------------------------------------------------------------------

  @Test
  void shouldReturnEmptyListWhenValuesIsEmpty() {
    server.enqueue(jsonOk("""
        {"meta": {}, "values": [], "status": "ok"}
        """));

    List<PricePoint> points =
        adapter.fetchPriceHistory(AssetType.STOCK, "AAPL", HoldingsHistoryRange.parse("30d"));

    assertThat(points).isEmpty();
  }

  @Test
  void shouldSkipEntriesWithMissingCloseField() {
    server.enqueue(
        jsonOk(
            """
        {
          "meta": {},
          "values": [
            {"datetime": "2026-05-22", "open": "309.0", "volume": "5000000"},
            {"datetime": "2026-05-21", "close": "307.80", "volume": "4500000"}
          ],
          "status": "ok"
        }
        """));

    List<PricePoint> points =
        adapter.fetchPriceHistory(AssetType.STOCK, "AAPL", HoldingsHistoryRange.parse("30d"));

    assertThat(points).hasSize(1);
    assertThat(points.get(0).price()).isEqualByComparingTo("307.80");
  }

  @Test
  void shouldSkipEntriesWithMissingDatetimeField() {
    server.enqueue(
        jsonOk(
            """
        {
          "meta": {},
          "values": [
            {"close": "309.50", "volume": "5000000"},
            {"datetime": "2026-05-21", "close": "307.80"}
          ],
          "status": "ok"
        }
        """));

    List<PricePoint> points =
        adapter.fetchPriceHistory(AssetType.STOCK, "AAPL", HoldingsHistoryRange.parse("30d"));

    assertThat(points).hasSize(1);
    assertThat(points.get(0).price()).isEqualByComparingTo("307.80");
  }

  // -------------------------------------------------------------------------
  // Parámetros de la request
  // -------------------------------------------------------------------------

  @Test
  void shouldIncludeRequiredQueryParamsAndNotUseDateFilters() throws Exception {
    // El plan gratuito de Twelve Data no admite start_date/end_date.
    // La URL solo debe contener symbol, interval, outputsize y apikey.
    server.enqueue(jsonOk("""
        {"meta": {}, "values": [], "status": "ok"}
        """));

    adapter.fetchPriceHistory(AssetType.STOCK, "AAPL", HoldingsHistoryRange.parse("30d"));

    RecordedRequest req = server.takeRequest(1, TimeUnit.SECONDS);
    String path = req.getPath();
    assertThat(path)
        .contains("symbol=AAPL")
        .contains("interval=1day")
        .contains("outputsize=")
        .contains("apikey=test-api-key")
        .doesNotContain("start_date")
        .doesNotContain("end_date");
  }

  @Test
  void shouldNormalizeSymbolToUpperCase() throws Exception {
    server.enqueue(jsonOk("""
        {"meta": {}, "values": [], "status": "ok"}
        """));

    adapter.fetchPriceHistory(AssetType.STOCK, "aapl", HoldingsHistoryRange.parse("30d"));

    RecordedRequest req = server.takeRequest(1, TimeUnit.SECONDS);
    assertThat(req.getPath()).contains("symbol=AAPL");
  }

  // -------------------------------------------------------------------------
  // Helper
  // -------------------------------------------------------------------------

  private MockResponse jsonOk(String body) {
    return new MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody(body);
  }
}
