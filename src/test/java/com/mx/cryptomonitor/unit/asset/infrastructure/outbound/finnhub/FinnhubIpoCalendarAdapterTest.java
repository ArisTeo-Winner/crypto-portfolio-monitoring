package com.mx.cryptomonitor.unit.asset.infrastructure.outbound.finnhub;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import com.mx.cryptomonitor.asset.application.port.out.IpoCalendarPort.IpoEntry;
import com.mx.cryptomonitor.asset.infrastructure.outbound.finnhub.FinnhubIpoCalendarAdapter;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

class FinnhubIpoCalendarAdapterTest {

  private static final String TOKEN = "super-secret-finnhub-token";

  private MockWebServer server;
  private FinnhubIpoCalendarAdapter adapter;

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
    adapter =
        new FinnhubIpoCalendarAdapter(
            WebClient.builder().baseUrl(server.url("/").toString()).build(), TOKEN);
  }

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
  }

  @Test
  void getRecentIposParsesSpcxAsPricedNasdaqListing() throws InterruptedException {
    server.enqueue(
        jsonOk(
            """
            {"ipoCalendar":[
              {
                "date": "2026-07-10",
                "exchange": "NASDAQ Global",
                "name": "SpaceCo Inc",
                "numberOfShares": 5000000,
                "price": "10.0-12.0",
                "status": "priced",
                "symbol": "SPCX",
                "totalSharesValue": 5000000000
              }
            ]}
            """));

    LocalDate from = LocalDate.of(2026, 6, 24);
    LocalDate to = LocalDate.of(2026, 7, 24);
    List<IpoEntry> result = adapter.getRecentIpos(from, to);

    assertThat(result).hasSize(1);
    IpoEntry entry = result.get(0);
    assertThat(entry.symbol()).isEqualTo("SPCX");
    assertThat(entry.name()).isEqualTo("SpaceCo Inc");
    assertThat(entry.exchange()).isEqualTo("NASDAQ Global");
    assertThat(entry.date()).isEqualTo(LocalDate.of(2026, 7, 10));
    assertThat(entry.status()).isEqualTo("priced");
    assertThat(entry.totalSharesValue()).isEqualTo(5_000_000_000L);

    RecordedRequest request = server.takeRequest();
    assertThat(request.getRequestUrl().encodedPath()).isEqualTo("/calendar/ipo");
    assertThat(request.getRequestUrl().queryParameter("from")).isEqualTo(from.toString());
    assertThat(request.getRequestUrl().queryParameter("to")).isEqualTo(to.toString());
    assertThat(request.getRequestUrl().queryParameter("token")).isEqualTo(TOKEN);
  }

  @Test
  void skipsEntriesWithoutSymbol() {
    server.enqueue(
        jsonOk(
            """
            {"ipoCalendar":[
              {"name":"No Symbol Co","status":"priced","exchange":"NASDAQ"}
            ]}
            """));

    List<IpoEntry> result = adapter.getRecentIpos(LocalDate.now(), LocalDate.now());

    assertThat(result).isEmpty();
  }

  @Test
  void missingApiKeyReturnsEmptyWithoutCallingFinnhub() {
    adapter =
        new FinnhubIpoCalendarAdapter(
            WebClient.builder().baseUrl(server.url("/").toString()).build(), "");

    List<IpoEntry> result = adapter.getRecentIpos(LocalDate.now(), LocalDate.now());

    assertThat(result).isEmpty();
    assertThat(server.getRequestCount()).isZero();
  }

  @Test
  void httpErrorReturnsEmptyListWithoutThrowing() {
    server.enqueue(new MockResponse().setResponseCode(500));

    List<IpoEntry> result = adapter.getRecentIpos(LocalDate.now(), LocalDate.now());

    assertThat(result).isEmpty();
  }

  @Test
  void emptyIpoCalendarReturnsEmptyList() {
    server.enqueue(jsonOk("{\"ipoCalendar\":[]}"));

    assertThat(adapter.getRecentIpos(LocalDate.now(), LocalDate.now())).isEmpty();
  }

  private MockResponse jsonOk(String body) {
    return new MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody(body);
  }
}
