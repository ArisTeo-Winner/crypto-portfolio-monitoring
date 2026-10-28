package com.mx.cryptomonitor.unit.marketdata.infrastructure.outbound.massive;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import com.mx.cryptomonitor.marketdata.application.port.out.DividendEventData;
import com.mx.cryptomonitor.marketdata.infrastructure.configuration.MassiveProperties;
import com.mx.cryptomonitor.marketdata.infrastructure.outbound.massive.MassiveDividendAdapter;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

class MassiveDividendAdapterTest {

  private MockWebServer server;
  private MassiveDividendAdapter adapter;

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
    MassiveProperties props = new MassiveProperties();
    props.setBaseUrl(server.url("/").toString().replaceAll("/$", ""));
    props.setApiKey("test-key");
    adapter = new MassiveDividendAdapter(WebClient.builder().build(), props);
  }

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
  }

  @Test
  void parsesGooglInauguralDividendPayload() {
    server.enqueue(
        jsonOk(
            """
            {"status":"OK","results":[
              {"id":"E1cdc29fc58066",
               "ticker":"GOOGL",
               "declaration_date":"2024-04-25",
               "ex_dividend_date":"2024-06-10",
               "record_date":"2024-06-10",
               "pay_date":"2024-06-17",
               "cash_amount":0.20,
               "currency":"USD",
               "frequency":4,
               "distribution_type":"recurring",
               "split_adjusted_cash_amount":0.20,
               "historical_adjustment_factor":0.994165}
            ]}
            """));

    List<DividendEventData> dividends = adapter.fetchDividends("GOOGL");

    assertThat(dividends).hasSize(1);
    DividendEventData d = dividends.get(0);
    assertThat(d.ticker()).isEqualTo("GOOGL");
    assertThat(d.providerEventId()).isEqualTo("E1cdc29fc58066");
    assertThat(d.declarationDate()).isEqualTo(LocalDate.of(2024, 4, 25));
    assertThat(d.exDividendDate()).isEqualTo(LocalDate.of(2024, 6, 10));
    assertThat(d.recordDate()).isEqualTo(LocalDate.of(2024, 6, 10));
    assertThat(d.paymentDate()).isEqualTo(LocalDate.of(2024, 6, 17));
    assertThat(d.cashAmount()).isEqualByComparingTo("0.20");
    assertThat(d.currency()).isEqualTo("USD");
    assertThat(d.frequency()).isEqualTo((short) 4);
    assertThat(d.distributionType()).isEqualTo("recurring");
    assertThat(d.splitAdjustedCashAmount()).isEqualByComparingTo("0.20");
    assertThat(d.historicalAdjustmentFactor()).isEqualByComparingTo("0.994165");
  }

  @Test
  void skipsRowsWithoutExDateOrCashAmount() {
    server.enqueue(
        jsonOk(
            """
            {"status":"OK","results":[
              {"id":"E1","ticker":"GOOGL","cash_amount":0.20},
              {"id":"E2","ticker":"GOOGL","ex_dividend_date":"2024-06-10"},
              {"id":"E3","ticker":"GOOGL","ex_dividend_date":"2024-09-09","cash_amount":0.20}
            ]}
            """));

    List<DividendEventData> dividends = adapter.fetchDividends("GOOGL");

    assertThat(dividends).hasSize(1);
    assertThat(dividends.get(0).providerEventId()).isEqualTo("E3");
  }

  @Test
  void returnsEmptyWhenApiKeyMissing() {
    MassiveProperties noKey = new MassiveProperties();
    noKey.setBaseUrl("http://localhost");
    noKey.setApiKey("");
    MassiveDividendAdapter unconfigured =
        new MassiveDividendAdapter(WebClient.builder().build(), noKey);

    assertThat(unconfigured.fetchDividends("GOOGL")).isEmpty();
  }

  private MockResponse jsonOk(String body) {
    return new MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody(body);
  }
}
