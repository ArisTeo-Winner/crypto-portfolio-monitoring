package com.mx.cryptomonitor.unit.marketdata.infrastructure.outbound.banxico;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import com.mx.cryptomonitor.marketdata.domain.exception.BanxicoException;
import com.mx.cryptomonitor.marketdata.domain.exception.BanxicoRateLimitException;
import com.mx.cryptomonitor.marketdata.infrastructure.outbound.banxico.BanxicoRateAdapter;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;

/**
 * Tests de {@link BanxicoRateAdapter} contra el endpoint batch /oportuno de Banxico SIE, usando
 * MockWebServer con un JSON de ejemplo con forma real de la respuesta.
 */
class BanxicoRateAdapterTest {

  private static final String TOKEN = "super-secret-banxico-token";

  private MockWebServer server;
  private BanxicoRateAdapter adapter;

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
    adapter =
        new BanxicoRateAdapter(
            WebClient.builder()
                .baseUrl(server.url("/").toString())
                .defaultHeader("Bmx-Token", TOKEN)
                .build());
  }

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
  }

  @Test
  void getCetesCurveParsesTheFiveTermsFromTheMostRecentDatum() {
    server.enqueue(jsonOk(fullCurveResponse()));

    Map<Integer, BigDecimal> curve = adapter.getCetesCurve();

    assertThat(curve).hasSize(5);
    assertThat(curve.get(28)).isEqualByComparingTo("6.18");
    assertThat(curve.get(91)).isEqualByComparingTo("6.49");
    assertThat(curve.get(182)).isEqualByComparingTo("6.75");
    assertThat(curve.get(364)).isEqualByComparingTo("6.93");
    assertThat(curve.get(728)).isEqualByComparingTo("7.94");
  }

  @Test
  void getCetesCurvePicksTheLatestDateWhenSeriesHasMultipleData() {
    server.enqueue(
        jsonOk(
            """
            {"bmx":{"series":[
              {"idSerie":"SF43936","datos":[
                {"fecha":"13/06/2023","dato":"11.38"},
                {"fecha":"27/06/2023","dato":"6.18"}
              ]}
            ]}}
            """));

    Map<Integer, BigDecimal> curve = adapter.getCetesCurve();

    assertThat(curve.get(28)).isEqualByComparingTo("6.18");
  }

  @Test
  void getCetesCurveIgnoresUnknownSeriesIds() {
    server.enqueue(
        jsonOk(
            """
            {"bmx":{"series":[
              {"idSerie":"SF00000","datos":[{"fecha":"27/06/2023","dato":"99.99"}]},
              {"idSerie":"SF43936","datos":[{"fecha":"27/06/2023","dato":"6.18"}]}
            ]}}
            """));

    Map<Integer, BigDecimal> curve = adapter.getCetesCurve();

    assertThat(curve).containsOnlyKeys(28);
  }

  @Test
  void getCetesRateReturnsExactTermWhenPresent() {
    server.enqueue(jsonOk(fullCurveResponse()));

    Optional<BigDecimal> rate = adapter.getCetesRate(91);

    assertThat(rate).isPresent();
    assertThat(rate.get()).isEqualByComparingTo("6.49");
  }

  /**
   * Regla documentada: plazo 60 esta a 32 dias de 28 y a 31 dias de 91 -> se usa 91 (mas cercano).
   */
  @Test
  void getCetesRateReturnsNearestTermWhenExactTermMissing() {
    server.enqueue(jsonOk(fullCurveResponse()));

    Optional<BigDecimal> rate = adapter.getCetesRate(60);

    assertThat(rate).isPresent();
    assertThat(rate.get()).isEqualByComparingTo("6.49");
  }

  @Test
  void sendsTokenOnlyAsHeaderNeverAsQueryParam() throws Exception {
    server.enqueue(jsonOk(fullCurveResponse()));

    adapter.getCetesCurve();

    RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
    assertThat(request).isNotNull();
    assertThat(request.getHeader("Bmx-Token")).isEqualTo(TOKEN);
    assertThat(request.getPath()).doesNotContain(TOKEN);
  }

  @Test
  void exceptionMessagesNeverContainTheToken() {
    server.enqueue(new MockResponse().setResponseCode(500).setBody("Internal Server Error"));

    assertThatThrownBy(() -> adapter.getCetesCurve())
        .isInstanceOf(BanxicoException.class)
        .satisfies(ex -> assertThat(ex.getMessage()).doesNotContain(TOKEN));
  }

  @Test
  void mapsHttp429ToRateLimitException() {
    server.enqueue(new MockResponse().setResponseCode(429).setBody("Too Many Requests"));

    assertThatThrownBy(() -> adapter.getCetesCurve()).isInstanceOf(BanxicoRateLimitException.class);
  }

  @Test
  void mapsHttp500ToBanxicoExceptionWithoutMisclassifying() {
    server.enqueue(new MockResponse().setResponseCode(500).setBody("Internal Server Error"));

    assertThatThrownBy(() -> adapter.getCetesCurve())
        .isInstanceOf(BanxicoException.class)
        .isNotInstanceOf(BanxicoRateLimitException.class);
  }

  @Test
  void mapsNetworkDisconnectToBanxicoException() {
    server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));

    assertThatThrownBy(() -> adapter.getCetesCurve()).isInstanceOf(BanxicoException.class);
  }

  private String fullCurveResponse() {
    return """
        {"bmx":{"series":[
          {"idSerie":"SF43936","datos":[{"fecha":"27/06/2023","dato":"6.18"}]},
          {"idSerie":"SF43939","datos":[{"fecha":"27/06/2023","dato":"6.49"}]},
          {"idSerie":"SF43942","datos":[{"fecha":"27/06/2023","dato":"6.75"}]},
          {"idSerie":"SF43945","datos":[{"fecha":"27/06/2023","dato":"6.93"}]},
          {"idSerie":"SF349785","datos":[{"fecha":"27/06/2023","dato":"7.94"}]}
        ]}}
        """;
  }

  private MockResponse jsonOk(String body) {
    return new MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody(body);
  }
}
