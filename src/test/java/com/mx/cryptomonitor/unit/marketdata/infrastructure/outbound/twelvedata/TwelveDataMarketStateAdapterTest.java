package com.mx.cryptomonitor.unit.marketdata.infrastructure.outbound.twelvedata;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import com.mx.cryptomonitor.marketdata.domain.model.Market;
import com.mx.cryptomonitor.marketdata.domain.model.RemoteMarketState;
import com.mx.cryptomonitor.marketdata.infrastructure.outbound.twelvedata.TwelveDataMarketStateAdapter;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

class TwelveDataMarketStateAdapterTest {

  private static final Instant NOW = Instant.parse("2026-11-16T04:11:08Z");

  private MockWebServer server;
  private TwelveDataMarketStateAdapter adapter;

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
    adapter =
        new TwelveDataMarketStateAdapter(
            WebClient.builder().build(),
            server.url("").toString().replaceAll("/$", ""),
            "test-key",
            Clock.fixed(NOW, ZoneOffset.UTC));
  }

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
  }

  @Test
  void closedMarketCountdownBecomesAnAbsoluteBell() {
    server.enqueue(
        jsonOk(
            """
            [{"name":"NYSE","code":"XNYS","country":"United States","is_market_open":false,
              "time_to_open":"10:18:52","time_to_close":"00:00:00","time_after_open":"00:00:00"}]
            """));

    Optional<RemoteMarketState> state = adapter.fetch(Market.US_EQUITY);

    assertThat(state)
        .contains(RemoteMarketState.closedUntil(Instant.parse("2026-11-16T14:30:00Z")));
  }

  @Test
  void countdownLongerThanADayIsADurationNotATimeOfDay() {
    server.enqueue(
        jsonOk(
            """
            [{"name":"BMV","code":"XMEX","country":"Mexico","is_market_open":false,
              "time_to_open":"34:18:52","time_to_close":"00:00:00","time_after_open":"00:00:00"}]
            """));

    Optional<RemoteMarketState> state = adapter.fetch(Market.MX_BMV);

    assertThat(state)
        .contains(RemoteMarketState.closedUntil(Instant.parse("2026-11-17T14:30:00Z")));
  }

  @Test
  void openMarketIsReportedAsOpen() {
    server.enqueue(
        jsonOk(
            """
            [{"name":"NYSE","code":"XNYS","is_market_open":true,
              "time_to_open":"00:00:00","time_to_close":"05:20:57","time_after_open":"02:39:03"}]
            """));

    assertThat(adapter.fetch(Market.US_EQUITY)).contains(RemoteMarketState.open());
  }

  @Test
  void requestsTheMarketByItsMicCode() throws Exception {
    server.enqueue(jsonOk("[]"));

    adapter.fetch(Market.MX_BMV);

    RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
    assertThat(request).isNotNull();
    assertThat(request.getPath())
        .startsWith("/market_state")
        .contains("code=XMEX")
        .contains("apikey=test-key");
  }

  @Test
  void malformedCountdownIsUnavailable() {
    server.enqueue(
        jsonOk(
            """
        [{"code":"XNYS","is_market_open":false,"time_to_open":"soon"}]
        """));

    assertThat(adapter.fetch(Market.US_EQUITY)).isEmpty();
  }

  @Test
  void closedWithZeroCountdownIsUnavailable() {
    server.enqueue(
        jsonOk(
            """
        [{"code":"XNYS","is_market_open":false,"time_to_open":"00:00:00"}]
        """));

    assertThat(adapter.fetch(Market.US_EQUITY)).isEmpty();
  }

  @Test
  void emptyListIsUnavailable() {
    server.enqueue(jsonOk("[]"));

    assertThat(adapter.fetch(Market.US_EQUITY)).isEmpty();
  }

  @Test
  void quotaErrorBodyIsUnavailable() {
    server.enqueue(
        jsonOk(
            """
            {"code":429,"message":"You have run out of API credits for the day.","status":"error"}
            """));

    assertThat(adapter.fetch(Market.US_EQUITY)).isEmpty();
  }

  @Test
  void httpErrorIsUnavailable() {
    server.enqueue(new MockResponse().setResponseCode(429));

    assertThat(adapter.fetch(Market.US_EQUITY)).isEmpty();
  }

  @Test
  void parseCountdownAcceptsHoursBeyondTwentyFourAndRejectsGarbage() {
    assertThat(TwelveDataMarketStateAdapter.parseCountdown("33:40:30"))
        .contains(Duration.ofHours(33).plusMinutes(40).plusSeconds(30));
    assertThat(TwelveDataMarketStateAdapter.parseCountdown("00:00:00")).contains(Duration.ZERO);
    assertThat(TwelveDataMarketStateAdapter.parseCountdown("10:75:00")).isEmpty();
    assertThat(TwelveDataMarketStateAdapter.parseCountdown("1 day, 2:00:00")).isEmpty();
    assertThat(TwelveDataMarketStateAdapter.parseCountdown(null)).isEmpty();
  }

  private MockResponse jsonOk(String body) {
    return new MockResponse()
        .setResponseCode(200)
        .setHeader("Content-Type", "application/json")
        .setBody(body);
  }
}
