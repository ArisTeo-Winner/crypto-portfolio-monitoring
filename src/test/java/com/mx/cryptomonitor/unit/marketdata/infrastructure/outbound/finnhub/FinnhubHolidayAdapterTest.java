package com.mx.cryptomonitor.unit.marketdata.infrastructure.outbound.finnhub;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import com.mx.cryptomonitor.marketdata.application.port.out.MarketHolidayData;
import com.mx.cryptomonitor.marketdata.infrastructure.outbound.finnhub.FinnhubHolidayAdapter;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

class FinnhubHolidayAdapterTest {

  private MockWebServer server;
  private FinnhubHolidayAdapter adapter;

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
    String baseUrl = server.url("/").toString().replaceAll("/$", "");
    adapter = new FinnhubHolidayAdapter(WebClient.builder().build(), baseUrl, "t");
  }

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
  }

  @Test
  void parsesFullClosureAsNullAndEarlyCloseFromTradingHour() {
    server.enqueue(
        jsonOk(
            """
            {"exchange":"US","timezone":"America/New_York","data":[
              {"eventName":"Thanksgiving Day","atDate":"2026-11-26","tradingHour":"","postMarket":""},
              {"eventName":"Thanksgiving Early Close","atDate":"2026-11-27",
               "tradingHour":"09:30-13:00","postMarket":"13:00-17:00"}
            ]}
            """));

    List<MarketHolidayData> holidays = adapter.fetchHolidays();

    assertThat(holidays).hasSize(2);
    assertThat(holidays.get(0).date()).isEqualTo(LocalDate.of(2026, 11, 26));
    assertThat(holidays.get(0).earlyClose()).isNull();
    assertThat(holidays.get(1).date()).isEqualTo(LocalDate.of(2026, 11, 27));
    assertThat(holidays.get(1).earlyClose()).isEqualTo(LocalTime.of(13, 0));
  }

  @Test
  void returnsEmptyWhenTokenMissing() {
    FinnhubHolidayAdapter noToken =
        new FinnhubHolidayAdapter(WebClient.builder().build(), "https://finnhub.io/api/v1", "");
    assertThat(noToken.fetchHolidays()).isEmpty();
  }

  private MockResponse jsonOk(String body) {
    return new MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody(body);
  }
}
