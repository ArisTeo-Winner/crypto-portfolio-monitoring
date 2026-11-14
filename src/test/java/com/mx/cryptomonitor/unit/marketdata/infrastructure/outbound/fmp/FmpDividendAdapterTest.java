package com.mx.cryptomonitor.unit.marketdata.infrastructure.outbound.fmp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import com.mx.cryptomonitor.marketdata.application.port.out.DividendEventData;
import com.mx.cryptomonitor.marketdata.application.port.out.DividendProviderUnavailableException;
import com.mx.cryptomonitor.marketdata.infrastructure.outbound.fmp.FmpDividendAdapter;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

class FmpDividendAdapterTest {

  private MockWebServer server;
  private FmpDividendAdapter adapter;

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
    String baseUrl = server.url("/").toString().replaceAll("/$", "");
    adapter = new FmpDividendAdapter(WebClient.builder(), baseUrl, "demo-key");
  }

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
  }

  @Test
  void parsesGooglIncludingAnnouncedFutureDividendAndFrequency() throws Exception {
    server.enqueue(
        jsonOk(
            """
            [
              {"symbol":"GOOGL","date":"2026-12-07","recordDate":"2026-12-07",
               "paymentDate":"2026-12-14","declarationDate":"2026-10-27","adjDividend":0.22,
               "dividend":0.22,"yield":0.307,"frequency":"Quarterly"},
              {"symbol":"GOOGL","date":"2025-03-10","recordDate":"2025-03-10",
               "paymentDate":"2025-03-17","declarationDate":"2025-02-04","adjDividend":0.2,
               "dividend":0.2,"yield":0.48,"frequency":"Quarterly"},
              {"symbol":"GOOGL","date":"2024-06-10","recordDate":"2024-06-10",
               "paymentDate":"2024-06-17","declarationDate":"2024-04-25","adjDividend":0.2,
               "dividend":0.2,"yield":0.11,"frequency":"Quarterly"}
            ]
            """));

    List<DividendEventData> dividends = adapter.fetchDividends("GOOGL");

    assertThat(dividends).hasSize(3);
    assertThat(dividends).allSatisfy(d -> assertThat(d.ticker()).isEqualTo("GOOGL"));
    assertThat(dividends).allSatisfy(d -> assertThat(d.currency()).isEqualTo("USD"));
    assertThat(dividends).allSatisfy(d -> assertThat(d.frequency()).isEqualTo((short) 4));

    // El dividendo futuro anunciado (lo que AlphaVantage omite) SÍ viene en FMP.
    DividendEventData future =
        dividends.stream()
            .filter(d -> d.exDividendDate().equals(LocalDate.of(2026, 12, 7)))
            .findFirst()
            .orElseThrow();
    assertThat(future.cashAmount()).isEqualByComparingTo("0.22");
    assertThat(future.splitAdjustedCashAmount()).isEqualByComparingTo("0.22");
    assertThat(future.paymentDate()).isEqualTo(LocalDate.of(2026, 12, 14));
    assertThat(future.declarationDate()).isEqualTo(LocalDate.of(2026, 10, 27));
    assertThat(future.providerEventId()).isEqualTo("fmp:GOOGL:2026-12-07");

    String path = server.takeRequest().getPath();
    assertThat(path).contains("/stable/dividends").contains("symbol=GOOGL");
  }

  @Test
  void throwsWhenProviderReturnsErrorBody() {
    server.enqueue(jsonOk("{\"Error Message\":\"Invalid API KEY\"}"));

    // ADR-0025 F2: fallo del proveedor => no concluyente (ERROR), nunca "no paga" (NONE).
    assertThatThrownBy(() -> adapter.fetchDividends("GOOGL"))
        .isInstanceOf(DividendProviderUnavailableException.class);
  }

  @Test
  void returnsEmptyWhenKeyMissing() {
    FmpDividendAdapter unconfigured =
        new FmpDividendAdapter(WebClient.builder(), "http://localhost", "");

    assertThat(unconfigured.fetchDividends("GOOGL")).isEmpty();
  }

  private MockResponse jsonOk(String body) {
    return new MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody(body);
  }
}
