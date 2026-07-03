package com.mx.cryptomonitor.unit.marketdata.infrastructure.outbound.databursatil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import com.mx.cryptomonitor.marketdata.domain.exception.DataBursatilException;
import com.mx.cryptomonitor.marketdata.domain.exception.DataBursatilInvalidSymbolException;
import com.mx.cryptomonitor.marketdata.domain.exception.DataBursatilRateLimitException;
import com.mx.cryptomonitor.marketdata.domain.model.BmvFxQuote;
import com.mx.cryptomonitor.marketdata.domain.model.BmvHistoricalPoint;
import com.mx.cryptomonitor.marketdata.domain.model.BmvQuote;
import com.mx.cryptomonitor.marketdata.infrastructure.configuration.DataBursatilProperties;
import com.mx.cryptomonitor.marketdata.infrastructure.outbound.databursatil.DataBursatilAdapter;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;

/**
 * Tests de {@link DataBursatilAdapter} contra los 4 endpoints reales de DataBursatil (cotizaciones,
 * historicos, intradia, divisas), usando MockWebServer con los JSON de ejemplo documentados por el
 * proveedor.
 */
class DataBursatilAdapterTest {

  private MockWebServer server;
  private DataBursatilAdapter adapter;

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
    adapter =
        new DataBursatilAdapter(
            WebClient.builder().baseUrl(server.url("/").toString()).build(),
            new DataBursatilProperties(server.url("/").toString(), "test-token"));
  }

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
  }

  // -------------------------------------------------------------------------
  // C2a — getQuotes: parseo anidado [emisora][bolsa][concepto]
  // -------------------------------------------------------------------------

  @Test
  void getQuotesParsesNestedResponseForRequestedBolsa() {
    server.enqueue(
        jsonOk(
            """
            {"GOOGL*":{"bmv":{"u":6020.0,"p":5992.41,"a":5980.00,"x":6050.00,"n":5950.00,\
            "c":-1.57,"m":-0.03,"v":125000.0,"o":5992.41,"i":748750000.00,\
            "f":"2026-06-25 14:00:00"}}}
            """));

    Map<String, BmvQuote> result = adapter.getQuotes(List.of("GOOGL*"), "BMV").block();

    assertThat(result).containsKey("GOOGL*");
    BmvQuote quote = result.get("GOOGL*");
    assertThat(quote.u()).isEqualTo(6020.0);
    assertThat(quote.p()).isEqualTo(5992.41);
    assertThat(quote.c()).isEqualTo(-1.57);
    assertThat(quote.f()).isEqualTo("2026-06-25 14:00:00");
  }

  @Test
  void getQuotesSendsSymbolsBolsaConceptoAndTokenAsQueryParams() throws Exception {
    server.enqueue(jsonOk("{}"));

    adapter.getQuotes(List.of("GOOGL*"), "BMV").block();

    RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
    assertThat(request).isNotNull();
    assertThat(request.getPath())
        .contains("/v2/cotizaciones")
        .contains("emisora_serie=GOOGL")
        .contains("bolsa=BMV")
        .contains("token=test-token");
  }

  // -------------------------------------------------------------------------
  // C2b — getQuotes: validacion de tamano de batch
  // -------------------------------------------------------------------------

  @Test
  void getQuotesRejectsBatchesLargerThan50Symbols() {
    List<String> symbols = IntStream.range(0, 51).mapToObj(i -> "SYM" + i).toList();

    assertThatThrownBy(() -> adapter.getQuotes(symbols, "BMV"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("50");
  }

  @Test
  void getQuotesAcceptsExactlyMaxBatchSizeWithoutThrowing() {
    List<String> symbols = IntStream.range(0, 50).mapToObj(i -> "SYM" + i).toList();
    server.enqueue(jsonOk("{}"));

    Map<String, BmvQuote> result = adapter.getQuotes(symbols, "BMV").block();

    assertThat(result).isEmpty();
  }

  @Test
  void getQuotesReturnsEmptyMapForEmptySymbolListWithoutHittingNetwork() {
    assertThat(adapter.getQuotes(List.of(), "BMV").block()).isEmpty();
    assertThat(server.getRequestCount()).isZero();
  }

  // -------------------------------------------------------------------------
  // C2c — getHistory: parseo [close, importe]
  // -------------------------------------------------------------------------

  @Test
  void getHistoryParsesClosePriceAndAmountTraded() {
    server.enqueue(jsonOk("""
        {"2025-02-26":[4905.57, 46554582.52]}
        """));

    LocalDate date = LocalDate.of(2025, 2, 26);
    Map<LocalDate, BmvHistoricalPoint> result = adapter.getHistory("AAPL*", date, date).block();

    assertThat(result).containsKey(date);
    BmvHistoricalPoint point = result.get(date);
    assertThat(point.closePrice()).isEqualByComparingTo("4905.57");
    assertThat(point.amountTraded()).isEqualByComparingTo("46554582.52");
  }

  @Test
  void getHistorySkipsEntriesWithFewerThanTwoValues() {
    server.enqueue(
        jsonOk(
            """
        {"2025-02-26":[4905.57, 46554582.52], "2025-02-27":[4900.00]}
        """));

    Map<LocalDate, BmvHistoricalPoint> result =
        adapter.getHistory("AAPL*", LocalDate.of(2025, 2, 26), LocalDate.of(2025, 2, 27)).block();

    assertThat(result).containsOnlyKeys(LocalDate.of(2025, 2, 26));
  }

  // -------------------------------------------------------------------------
  // C2d — getFxRate: parseo divisas
  // -------------------------------------------------------------------------

  @Test
  void getFxRateParsesUsdMxnQuote() {
    server.enqueue(
        jsonOk(
            """
        {"USDMXN":{"u":17.5249,"c":0.09,"m":0.0149},"t":"2026-06-26 02:31:00"}
        """));

    BmvFxQuote fx = adapter.getFxRate("USDMXN").block();

    assertThat(fx.u()).isEqualTo(17.5249);
    assertThat(fx.c()).isEqualTo(0.09);
    assertThat(fx.m()).isEqualTo(0.0149);
    assertThat(fx.t()).isEqualTo("2026-06-26 02:31:00");
  }

  @Test
  void getFxRateThrowsInvalidSymbolWhenTickerMissingFromResponse() {
    server.enqueue(jsonOk("""
        {"t":"2026-06-26 02:31:00"}
        """));

    assertThatThrownBy(() -> adapter.getFxRate("USDMXN").block())
        .isInstanceOf(DataBursatilInvalidSymbolException.class);
  }

  // -------------------------------------------------------------------------
  // C2e — mapeo de errores HTTP y de red
  // -------------------------------------------------------------------------

  @Test
  void mapsHttp429ToRateLimitException() {
    server.enqueue(new MockResponse().setResponseCode(429).setBody("Too Many Requests"));

    assertThatThrownBy(() -> adapter.getQuotes(List.of("AAPL*"), "BMV").block())
        .isInstanceOf(DataBursatilRateLimitException.class);
  }

  @Test
  void mapsHttp404ToInvalidSymbolException() {
    server.enqueue(new MockResponse().setResponseCode(404).setBody("Not Found"));

    assertThatThrownBy(() -> adapter.getQuotes(List.of("AAPL*"), "BMV").block())
        .isInstanceOf(DataBursatilInvalidSymbolException.class);
  }

  @Test
  void mapsHttp500ToDataBursatilExceptionWithoutMisclassifying() {
    server.enqueue(new MockResponse().setResponseCode(500).setBody("Internal Server Error"));

    assertThatThrownBy(() -> adapter.getQuotes(List.of("AAPL*"), "BMV").block())
        .isInstanceOf(DataBursatilException.class)
        .isNotInstanceOf(DataBursatilRateLimitException.class)
        .isNotInstanceOf(DataBursatilInvalidSymbolException.class);
  }

  @Test
  void mapsNetworkDisconnectToDataBursatilException() {
    server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));

    assertThatThrownBy(() -> adapter.getQuotes(List.of("AAPL*"), "BMV").block())
        .isInstanceOf(DataBursatilException.class);
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
