package com.mx.cryptomonitor.unit.marketdata.infrastructure.outbound.databursatil;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import com.mx.cryptomonitor.marketdata.application.port.out.DividendEventData;
import com.mx.cryptomonitor.marketdata.infrastructure.configuration.DataBursatilProperties;
import com.mx.cryptomonitor.marketdata.infrastructure.outbound.databursatil.DataBursatilDividendAdapter;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

class DataBursatilDividendAdapterTest {

  private MockWebServer server;
  private DataBursatilDividendAdapter adapter;

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
    String baseUrl = server.url("/").toString().replaceAll("/$", "");
    adapter =
        new DataBursatilDividendAdapter(
            WebClient.builder().baseUrl(baseUrl).build(),
            new DataBursatilProperties(baseUrl, "test-token"));
  }

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
  }

  @Test
  void parsesBimboDividends_dedupesByPayDate_andTakesExDateFromReciente() throws Exception {
    server.enqueue(
        jsonOk(
            """
            {"BIMBO":{"A":{"razon_social":"GRUPO BIMBO, S.A.B. DE C.V.","bolsa":"BMV",
              "dividendos":{
                "reciente":{"2026-05-13":{"pago":1.075,"tipo":"DIVIDENDO EN EFECTIVO",
                   "divisa":"MXN","fechaexcupon":"2026-05-12"}},
                "historico":{
                   "2021-05-17":{"pago":1.0,"tipo":"DIVIDENDO EN EFECTIVO"},
                   "2022-05-19":{"pago":0.65,"tipo":"DIVIDENDO EN EFECTIVO"},
                   "2022-11-28":{"pago":0.65,"tipo":"DIVIDENDO EN EFECTIVO"},
                   "2023-05-18":{"pago":0.78,"tipo":"DIVIDENDO EN EFECTIVO"},
                   "2024-05-14":{"pago":0.94,"tipo":"DIVIDENDO EN EFECTIVO"},
                   "2025-05-14":{"pago":1.0,"tipo":"DIVIDENDO EN EFECTIVO"},
                   "2026-05-13":{"pago":1.075,"tipo":"DIVIDENDO EN EFECTIVO"}}}}}}
            """));

    List<DividendEventData> dividends = adapter.fetchDividends("BIMBOA.MX");

    // 7 fechas de pago únicas (2026-05-13 aparece en reciente e historico → 1 evento).
    assertThat(dividends).hasSize(7);
    assertThat(dividends).allSatisfy(d -> assertThat(d.ticker()).isEqualTo("BIMBOA"));
    assertThat(dividends).allSatisfy(d -> assertThat(d.currency()).isEqualTo("MXN"));
    assertThat(dividends).allSatisfy(d -> assertThat(d.distributionType()).isEqualTo("recurring"));

    DividendEventData latest =
        dividends.stream()
            .filter(d -> d.paymentDate().equals(LocalDate.of(2026, 5, 13)))
            .findFirst()
            .orElseThrow();
    assertThat(latest.cashAmount()).isEqualByComparingTo("1.075");
    assertThat(latest.exDividendDate()).isEqualTo(LocalDate.of(2026, 5, 12)); // fechaexcupon
    assertThat(latest.providerEventId()).isEqualTo("databursatil:BIMBOA:2026-05-13");

    DividendEventData y2024 =
        dividends.stream()
            .filter(d -> d.paymentDate().equals(LocalDate.of(2024, 5, 14)))
            .findFirst()
            .orElseThrow();
    assertThat(y2024.cashAmount()).isEqualByComparingTo("0.94");
    assertThat(y2024.exDividendDate()).isEqualTo(LocalDate.of(2024, 5, 14)); // histórico: proxy

    assertThat(server.takeRequest().getPath()).contains("/v2/emisoras").contains("letra=BIMB");
  }

  @Test
  void returnsEmptyWhenTokenMissing() {
    DataBursatilDividendAdapter unconfigured =
        new DataBursatilDividendAdapter(
            WebClient.builder().build(), new DataBursatilProperties("http://localhost", ""));

    assertThat(unconfigured.fetchDividends("BIMBOA")).isEmpty();
  }

  @Test
  void returnsEmptyWhenNoDividendsNode() {
    server.enqueue(jsonOk("{\"BIMBO\":{\"A\":{\"razon_social\":\"GRUPO BIMBO\"}}}"));

    assertThat(adapter.fetchDividends("BIMBOA")).isEmpty();
  }

  private MockResponse jsonOk(String body) {
    return new MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody(body);
  }
}
