package com.mx.cryptomonitor.unit.marketdata.infrastructure.outbound.massive;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import com.mx.cryptomonitor.marketdata.application.port.out.MarketHolidayData;
import com.mx.cryptomonitor.marketdata.infrastructure.configuration.MassiveProperties;
import com.mx.cryptomonitor.marketdata.infrastructure.outbound.massive.MassiveUpcomingHolidayAdapter;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

class MassiveUpcomingHolidayAdapterTest {

  private MockWebServer server;
  private MassiveUpcomingHolidayAdapter adapter;

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
    MassiveProperties props = new MassiveProperties();
    props.setBaseUrl(server.url("/").toString().replaceAll("/$", ""));
    props.setApiKey("k");
    adapter = new MassiveUpcomingHolidayAdapter(WebClient.builder().build(), props);
  }

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
  }

  @Test
  void parsesFullClosureAndEarlyCloseDedupingNyse() {
    server.enqueue(
        jsonOk(
            """
            [
              {"date":"2026-11-26","exchange":"NYSE","name":"Thanksgiving","status":"closed"},
              {"date":"2026-11-26","exchange":"NASDAQ","name":"Thanksgiving","status":"closed"},
              {"date":"2026-11-27","exchange":"NYSE","name":"Thanksgiving",
               "open":"2026-11-27T14:30:00.000Z","close":"2026-11-27T18:00:00.000Z","status":"early-close"}
            ]
            """));

    List<MarketHolidayData> holidays = adapter.fetchHolidays();

    assertThat(holidays).hasSize(2); // NASDAQ duplicado del 26 descartado
    assertThat(holidays.get(0).date()).isEqualTo(LocalDate.of(2026, 11, 26));
    assertThat(holidays.get(0).earlyClose()).isNull();
    assertThat(holidays.get(1).date()).isEqualTo(LocalDate.of(2026, 11, 27));
    assertThat(holidays.get(1).earlyClose()).isEqualTo(LocalTime.of(13, 0)); // 18:00Z -> 13:00 EST
  }

  @Test
  void returnsEmptyWhenApiKeyMissing() {
    MassiveProperties noKey = new MassiveProperties();
    noKey.setBaseUrl("http://localhost");
    noKey.setApiKey("");
    MassiveUpcomingHolidayAdapter unconfigured =
        new MassiveUpcomingHolidayAdapter(WebClient.builder().build(), noKey);
    assertThat(unconfigured.fetchHolidays()).isEmpty();
  }

  private MockResponse jsonOk(String body) {
    return new MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody(body);
  }
}
