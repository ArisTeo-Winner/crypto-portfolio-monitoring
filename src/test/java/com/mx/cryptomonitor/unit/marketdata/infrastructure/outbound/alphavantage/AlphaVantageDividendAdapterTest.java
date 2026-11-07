package com.mx.cryptomonitor.unit.marketdata.infrastructure.outbound.alphavantage;

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
import com.mx.cryptomonitor.marketdata.infrastructure.configuration.AlphaVantageProperties;
import com.mx.cryptomonitor.marketdata.infrastructure.outbound.alphavantage.AlphaVantageDividendAdapter;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

class AlphaVantageDividendAdapterTest {

  private MockWebServer server;
  private AlphaVantageDividendAdapter adapter;

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
    adapter = new AlphaVantageDividendAdapter(WebClient.builder().build(), props("demo"));
  }

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
  }

  private AlphaVantageProperties props(String key) {
    AlphaVantageProperties p = new AlphaVantageProperties();
    p.setBaseUrl(server.url("/").toString().replaceAll("/$", ""));
    p.setApiKey(key);
    return p;
  }

  @Test
  void parsesGooglAndFiltersArtifactRow() throws Exception {
    server.enqueue(
        jsonOk(
            """
            {"symbol":"GOOGL","data":[
              {"ex_dividend_date":"2026-09-04","declaration_date":"2026-07-22",
               "record_date":"2026-09-07","payment_date":"2026-09-14","amount":"0.22"},
              {"ex_dividend_date":"2025-03-10","declaration_date":"2025-02-04",
               "record_date":"2025-03-10","payment_date":"2025-03-17","amount":"0.2"},
              {"ex_dividend_date":"2024-06-10","declaration_date":"2024-04-25",
               "record_date":"2024-06-10","payment_date":"2024-06-17","amount":"0.2"},
              {"ex_dividend_date":"2014-04-03","declaration_date":"None",
               "record_date":"None","payment_date":"None","amount":"567.971668498"}
            ]}
            """));

    List<DividendEventData> dividends = adapter.fetchDividends("GOOGL");

    // La fila-artefacto 2014 (payment_date="None", amount=567.97) se descarta: 3 reales.
    assertThat(dividends).hasSize(3);
    assertThat(dividends)
        .noneSatisfy(d -> assertThat(d.exDividendDate()).isEqualTo(LocalDate.of(2014, 4, 3)));
    assertThat(dividends).allSatisfy(d -> assertThat(d.currency()).isEqualTo("USD"));
    assertThat(dividends)
        .allSatisfy(d -> assertThat(d.cashAmount()).isLessThan(java.math.BigDecimal.ONE));

    DividendEventData y2025 =
        dividends.stream()
            .filter(d -> d.exDividendDate().equals(LocalDate.of(2025, 3, 10)))
            .findFirst()
            .orElseThrow();
    assertThat(y2025.cashAmount()).isEqualByComparingTo("0.2");
    assertThat(y2025.paymentDate()).isEqualTo(LocalDate.of(2025, 3, 17));
    assertThat(y2025.providerEventId()).isEqualTo("alphavantage:GOOGL:2025-03-10");

    String path = server.takeRequest().getPath();
    assertThat(path).contains("function=DIVIDENDS").contains("symbol=GOOGL");
  }

  @Test
  void throwsOnRateLimitEnvelope() {
    server.enqueue(jsonOk("{\"Information\":\"rate limit reached (25/day)\"}"));

    // ADR-0025 F2: envelope de cuota/error => no concluyente (ERROR), nunca "no paga" (NONE).
    assertThatThrownBy(() -> adapter.fetchDividends("GOOGL"))
        .isInstanceOf(DividendProviderUnavailableException.class);
  }

  @Test
  void returnsEmptyWhenKeyMissing() {
    AlphaVantageDividendAdapter unconfigured =
        new AlphaVantageDividendAdapter(WebClient.builder().build(), props(""));

    assertThat(unconfigured.fetchDividends("GOOGL")).isEmpty();
  }

  private MockResponse jsonOk(String body) {
    return new MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody(body);
  }
}
