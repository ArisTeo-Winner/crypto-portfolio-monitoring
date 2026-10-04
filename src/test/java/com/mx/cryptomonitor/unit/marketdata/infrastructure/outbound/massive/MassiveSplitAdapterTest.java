package com.mx.cryptomonitor.unit.marketdata.infrastructure.outbound.massive;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import com.mx.cryptomonitor.marketdata.application.port.out.StockSplitData;
import com.mx.cryptomonitor.marketdata.infrastructure.configuration.MassiveProperties;
import com.mx.cryptomonitor.marketdata.infrastructure.outbound.massive.MassiveSplitAdapter;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

class MassiveSplitAdapterTest {

  private MockWebServer server;
  private MassiveSplitAdapter adapter;

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
    MassiveProperties props = new MassiveProperties();
    props.setBaseUrl(server.url("/").toString().replaceAll("/$", ""));
    props.setApiKey("test-key");
    adapter = new MassiveSplitAdapter(WebClient.builder().build(), props);
  }

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
  }

  @Test
  void parsesWetoReverseSplitWithMultiplier() {
    server.enqueue(
        jsonOk(
            """
            {"status":"OK","results":[
              {"execution_date":"2026-08-03","split_from":100.0,"split_to":1.0,
               "ticker":"WETO","adjustment_type":"reverse_split","historical_adjustment_factor":100.0}
            ]}
            """));

    List<StockSplitData> splits = adapter.fetchSplits("WETO");

    assertThat(splits).hasSize(1);
    assertThat(splits.get(0).ticker()).isEqualTo("WETO");
    assertThat(splits.get(0).executionDate()).isEqualTo(LocalDate.of(2026, 8, 3));
    assertThat(splits.get(0).shareMultiplier()).isEqualByComparingTo("0.01"); // 1/100
  }

  @Test
  void parsesForwardSplitMultiplierGreaterThanOne() {
    server.enqueue(
        jsonOk(
            """
            {"status":"OK","results":[
              {"execution_date":"2026-11-05","split_from":1.0,"split_to":3.0,
               "ticker":"SOXX","adjustment_type":"forward_split"}
            ]}
            """));

    List<StockSplitData> splits = adapter.fetchSplits("SOXX");

    assertThat(splits.get(0).shareMultiplier()).isEqualByComparingTo("3"); // 3/1
  }

  @Test
  void returnsEmptyWhenApiKeyMissing() {
    MassiveProperties noKey = new MassiveProperties();
    noKey.setBaseUrl("http://localhost");
    noKey.setApiKey("");
    MassiveSplitAdapter unconfigured = new MassiveSplitAdapter(WebClient.builder().build(), noKey);

    assertThat(unconfigured.fetchSplits("WETO")).isEmpty();
  }

  private MockResponse jsonOk(String body) {
    return new MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody(body);
  }
}
