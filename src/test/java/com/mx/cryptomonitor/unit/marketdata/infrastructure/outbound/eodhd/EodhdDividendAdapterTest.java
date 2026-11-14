package com.mx.cryptomonitor.unit.marketdata.infrastructure.outbound.eodhd;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import com.mx.cryptomonitor.marketdata.application.port.out.DividendEventData;
import com.mx.cryptomonitor.marketdata.infrastructure.configuration.EodhdProperties;
import com.mx.cryptomonitor.marketdata.infrastructure.outbound.eodhd.EodhdDividendAdapter;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

class EodhdDividendAdapterTest {

  private MockWebServer server;
  private EodhdDividendAdapter adapter;

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
    adapter = new EodhdDividendAdapter(WebClient.builder().build(), props("demo-token"));
  }

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
  }

  private EodhdProperties props(String token) {
    EodhdProperties p = new EodhdProperties();
    p.setBaseUrl(server.url("/").toString().replaceAll("/$", ""));
    p.setApiToken(token);
    p.setDefaultSuffix(".US");
    return p;
  }

  @Test
  void mapsAdjustedAndUnadjustedAndAppendsUsSuffix() throws Exception {
    server.enqueue(
        jsonOk(
            """
            [{"date":"2024-06-10","declarationDate":"2024-04-25","recordDate":"2024-06-10",
              "paymentDate":"2024-06-17","period":"Quarterly","value":0.20,"unadjustedValue":0.21,
              "currency":"USD"}]
            """));

    List<DividendEventData> dividends = adapter.fetchDividends("GOOG");

    assertThat(dividends).hasSize(1);
    DividendEventData d = dividends.get(0);
    assertThat(d.ticker()).isEqualTo("GOOG"); // símbolo de entrada, sin sufijo EOD
    assertThat(d.exDividendDate()).isEqualTo(LocalDate.of(2024, 6, 10));
    assertThat(d.declarationDate()).isEqualTo(LocalDate.of(2024, 4, 25));
    assertThat(d.paymentDate()).isEqualTo(LocalDate.of(2024, 6, 17));
    assertThat(d.cashAmount()).isEqualByComparingTo("0.21"); // unadjustedValue = DPS bruto
    assertThat(d.splitAdjustedCashAmount()).isEqualByComparingTo("0.20"); // value = ajustado
    assertThat(d.currency()).isEqualTo("USD");
    assertThat(d.frequency()).isEqualTo((short) 4);
    assertThat(d.distributionType()).isEqualTo("recurring");
    assertThat(d.providerEventId()).isEqualTo("eodhd:GOOG:2024-06-10");
    assertThat(server.takeRequest().getPath()).contains("/api/div/GOOG.US");
  }

  @Test
  void respectsExplicitMxSuffixAndNativeCurrency() throws Exception {
    server.enqueue(
        jsonOk(
            """
            [{"date":"2024-12-07","paymentDate":"2024-12-14","period":"Annual","value":1.85,
              "unadjustedValue":1.85,"currency":"MXN"}]
            """));

    List<DividendEventData> dividends = adapter.fetchDividends("BIMBOA.MX");

    assertThat(dividends).hasSize(1);
    assertThat(dividends.get(0).ticker()).isEqualTo("BIMBOA.MX");
    assertThat(dividends.get(0).currency()).isEqualTo("MXN");
    assertThat(dividends.get(0).frequency()).isEqualTo((short) 1);
    assertThat(server.takeRequest().getPath()).contains("/api/div/BIMBOA.MX");
  }

  @Test
  void skipsRowsWithoutDateOrValue() {
    server.enqueue(
        jsonOk(
            """
            [{"value":0.20},
             {"date":"2024-06-10"},
             {"date":"2024-09-09","value":0.20,"unadjustedValue":0.20}]
            """));

    List<DividendEventData> dividends = adapter.fetchDividends("GOOG");

    assertThat(dividends).hasSize(1);
    assertThat(dividends.get(0).exDividendDate()).isEqualTo(LocalDate.of(2024, 9, 9));
  }

  @Test
  void returnsEmptyWhenTokenMissing() {
    EodhdDividendAdapter unconfigured =
        new EodhdDividendAdapter(WebClient.builder().build(), props(""));

    assertThat(unconfigured.fetchDividends("GOOG")).isEmpty();
  }

  @Test
  void returnsEmptyOnErrorStatusWithoutThrowing() {
    server.enqueue(new MockResponse().setResponseCode(404).setBody("{\"message\":\"Not found\"}"));

    assertThat(adapter.fetchDividends("NOPE")).isEmpty();
  }

  private MockResponse jsonOk(String body) {
    return new MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody(body);
  }
}
