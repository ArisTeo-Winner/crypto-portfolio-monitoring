package com.mx.cryptomonitor.unit.marketdata.infrastructure.outbound.twelvedata;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import com.mx.cryptomonitor.marketdata.domain.model.StockQuote;
import com.mx.cryptomonitor.marketdata.infrastructure.outbound.twelvedata.TwelveDataEndOfDayAdapter;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

class TwelveDataEndOfDayAdapterTest {

  private static final Instant NOW = Instant.parse("2026-11-13T21:05:00Z");

  private MockWebServer server;
  private TwelveDataEndOfDayAdapter adapter;

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
    adapter =
        new TwelveDataEndOfDayAdapter(
            WebClient.builder().build(),
            server.url("").toString().replaceAll("/$", ""),
            "test-key",
            Clock.fixed(NOW, ZoneOffset.UTC),
            Duration.ofHours(1));
  }

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
  }

  @Test
  void returnsTheCloseWithItsSessionDate() throws Exception {
    server.enqueue(
        jsonOk(
            """
            {"symbol":"AAPL","exchange":"NASDAQ","mic_code":"XNGS","currency":"USD",
             "datetime":"2026-11-13","timestamp":1794603540,"close":"356.26999"}
            """));

    Optional<StockQuote> quote = adapter.find("AAPL");

    assertThat(quote)
        .contains(
            new StockQuote(
                new BigDecimal("356.26999"), NOW, "twelvedata-eod", LocalDate.of(2026, 11, 13)));
    RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
    assertThat(request).isNotNull();
    assertThat(request.getPath())
        .startsWith("/eod")
        .contains("symbol=AAPL")
        .contains("apikey=test-key");
  }

  @Test
  void datetimeWithTimeKeepsOnlyTheSessionDate() {
    server.enqueue(
        jsonOk("""
        {"datetime":"2026-11-13 15:59:00","close":"10.5"}
        """));

    assertThat(adapter.find("KO"))
        .map(StockQuote::sessionDate)
        .contains(LocalDate.of(2026, 11, 13));
  }

  @Test
  void missingFieldsMeanNoClose() {
    server.enqueue(jsonOk("""
        {"symbol":"AAPL","datetime":"2026-11-13"}
        """));

    assertThat(adapter.find("AAPL")).isEmpty();
  }

  @Test
  void unparseableDateMeansNoClose() {
    server.enqueue(jsonOk("""
        {"datetime":"yesterday","close":"10.5"}
        """));

    assertThat(adapter.find("AAPL")).isEmpty();
  }

  @Test
  void quotaErrorStopsFurtherCallsForTheRetryInterval() {
    server.enqueue(
        jsonOk(
            """
            {"code":429,"message":"You have run out of API credits for the day.","status":"error"}
            """));

    assertThat(adapter.find("AAPL")).isEmpty();
    assertThat(adapter.find("MSFT")).isEmpty();

    assertThat(server.getRequestCount()).isEqualTo(1);
  }

  @Test
  void httpAuthErrorStopsFurtherCallsForTheRetryInterval() {
    server.enqueue(new MockResponse().setResponseCode(401));

    assertThat(adapter.find("AAPL")).isEmpty();
    assertThat(adapter.find("MSFT")).isEmpty();

    assertThat(server.getRequestCount()).isEqualTo(1);
  }

  @Test
  void unknownSymbolDoesNotBlockOtherSymbols() {
    server.enqueue(
        jsonOk("""
        {"code":404,"message":"symbol not found","status":"error"}
        """));
    server.enqueue(jsonOk("""
        {"datetime":"2026-11-13","close":"99.27"}
        """));

    assertThat(adapter.find("WETO")).isEmpty();
    assertThat(adapter.find("KO")).map(StockQuote::price).contains(new BigDecimal("99.27"));
  }

  private MockResponse jsonOk(String body) {
    return new MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(body);
  }
}
