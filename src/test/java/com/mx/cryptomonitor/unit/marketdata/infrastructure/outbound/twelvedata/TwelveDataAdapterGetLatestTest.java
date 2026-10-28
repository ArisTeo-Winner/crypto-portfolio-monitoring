package com.mx.cryptomonitor.unit.marketdata.infrastructure.outbound.twelvedata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import com.mx.cryptomonitor.marketdata.domain.exception.TwelveDataAuthException;
import com.mx.cryptomonitor.marketdata.domain.exception.TwelveDataException;
import com.mx.cryptomonitor.marketdata.domain.exception.TwelveDataInvalidSymbolException;
import com.mx.cryptomonitor.marketdata.domain.exception.TwelveDataRateLimitException;
import com.mx.cryptomonitor.marketdata.infrastructure.outbound.twelvedata.TwelveDataAdapter;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;

/**
 * Tests de la clase {@link TwelveDataAdapter#getLatest(String)}.
 *
 * <p>Cubre todos los escenarios de respuesta del endpoint {@code /price}: éxito, errores en body
 * (HTTP 200 con status error), errores HTTP (4xx, 5xx) y fallos de red.
 */
class TwelveDataAdapterGetLatestTest {

  private MockWebServer server;
  private TwelveDataAdapter adapter;

  @BeforeEach
  void setUp() throws Exception {
    server = new MockWebServer();
    server.start();
    adapter =
        new TwelveDataAdapter(WebClient.builder().build(), server.url("/").toString(), "test-key");
  }

  @AfterEach
  void tearDown() throws Exception {
    server.shutdown();
  }

  // -------------------------------------------------------------------------
  // Escenarios de éxito
  // -------------------------------------------------------------------------

  @Test
  void shouldReturnPriceWhenApiRespondsSuccessfully() {
    server.enqueue(jsonOk("""
        {"price": "308.88000"}
        """));

    Optional<BigDecimal> result = adapter.getLatest("AAPL");

    assertThat(result).isPresent();
    assertThat(result.get()).isEqualByComparingTo("308.88000");
  }

  @Test
  void shouldReturnEmptyWhenPriceFieldIsMissing() {
    server.enqueue(jsonOk("""
        {"status": "ok"}
        """));

    Optional<BigDecimal> result = adapter.getLatest("AAPL");

    assertThat(result).isEmpty();
  }

  @Test
  void shouldReturnEmptyWhenPriceFieldIsEmpty() {
    server.enqueue(jsonOk("""
        {"price": ""}
        """));

    Optional<BigDecimal> result = adapter.getLatest("AAPL");

    assertThat(result).isEmpty();
  }

  @Test
  void shouldSendSymbolAndApiKeyAsQueryParams() throws Exception {
    server.enqueue(jsonOk("""
        {"price": "100.00"}
        """));

    adapter.getLatest("MSFT");

    RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
    assertThat(request).isNotNull();
    assertThat(request.getPath()).contains("symbol=MSFT").contains("apikey=test-key");
  }

  // -------------------------------------------------------------------------
  // Errores en body con HTTP 200 (Twelve Data retorna status:"error")
  // -------------------------------------------------------------------------

  @Test
  void shouldThrowTwelveDataRateLimitExceptionWhenBodyReturnsCode429() {
    server.enqueue(
        jsonOk(
            """
        {
          "code": 429,
          "message": "You have run out of API credits for the current minute.",
          "status": "error"
        }
        """));

    assertThatThrownBy(() -> adapter.getLatest("AAPL"))
        .isInstanceOf(TwelveDataRateLimitException.class)
        .hasMessageContaining("run out of API credits");
  }

  @Test
  void shouldThrowTwelveDataInvalidSymbolExceptionWhenBodyReturnsCode400AndSymbolNotValid() {
    server.enqueue(
        jsonOk(
            """
        {
          "code": 400,
          "message": "**INVALIDO** is not valid.",
          "status": "error"
        }
        """));

    assertThatThrownBy(() -> adapter.getLatest("INVALIDO"))
        .isInstanceOf(TwelveDataInvalidSymbolException.class);
  }

  @Test
  void shouldThrowUpstreamExceptionWhenBodyCode400AndNoDataForDates() {
    // code=400 + mensaje de fechas → el símbolo existe pero no hay datos → permitir fallback
    server.enqueue(
        jsonOk(
            """
        {
          "code": 400,
          "message": "No data is available on the specified dates. Try setting different start/end dates.",
          "status": "error"
        }
        """));

    assertThatThrownBy(() -> adapter.getLatest("IBM"))
        .isInstanceOf(TwelveDataException.class)
        .isNotInstanceOf(TwelveDataInvalidSymbolException.class);
  }

  @Test
  void shouldThrowTwelveDataInvalidSymbolExceptionWhenBodyReturnsCode404() {
    server.enqueue(
        jsonOk(
            """
        {
          "code": 404,
          "message": "Symbol not found.",
          "status": "error"
        }
        """));

    assertThatThrownBy(() -> adapter.getLatest("XXXXX"))
        .isInstanceOf(TwelveDataInvalidSymbolException.class);
  }

  @Test
  void shouldThrowTwelveDataAuthExceptionWhenBodyReturnsCode401() {
    server.enqueue(
        jsonOk(
            """
        {
          "code": 401,
          "message": "Your API key is invalid.",
          "status": "error"
        }
        """));

    assertThatThrownBy(() -> adapter.getLatest("AAPL"))
        .isInstanceOf(TwelveDataAuthException.class)
        .hasMessageContaining("invalida o expirada");
  }

  @Test
  void shouldThrowTwelveDataAuthExceptionWhenBodyReturnsCode403() {
    server.enqueue(
        jsonOk(
            """
        {
          "code": 403,
          "message": "Your API key does not have access to this resource.",
          "status": "error"
        }
        """));

    assertThatThrownBy(() -> adapter.getLatest("AAPL"))
        .isInstanceOf(TwelveDataAuthException.class)
        .hasMessageContaining("invalida o expirada");
  }

  @Test
  void shouldThrowTwelveDataExceptionWhenBodyReturnsUnknownErrorCode() {
    server.enqueue(
        jsonOk(
            """
        {
          "code": 503,
          "message": "Service temporarily unavailable.",
          "status": "error"
        }
        """));

    assertThatThrownBy(() -> adapter.getLatest("AAPL"))
        .isInstanceOf(TwelveDataException.class)
        .isNotInstanceOf(TwelveDataAuthException.class)
        .isNotInstanceOf(TwelveDataRateLimitException.class)
        .isNotInstanceOf(TwelveDataInvalidSymbolException.class);
  }

  // -------------------------------------------------------------------------
  // Errores a nivel HTTP
  // -------------------------------------------------------------------------

  @Test
  void shouldThrowTwelveDataAuthExceptionOnHttp401() {
    server.enqueue(new MockResponse().setResponseCode(401).setBody("Unauthorized"));

    assertThatThrownBy(() -> adapter.getLatest("AAPL"))
        .isInstanceOf(TwelveDataAuthException.class)
        .hasMessageContaining("invalida o expirada")
        .hasMessageContaining("401");
  }

  @Test
  void shouldThrowTwelveDataAuthExceptionOnHttp403() {
    server.enqueue(new MockResponse().setResponseCode(403).setBody("Forbidden"));

    assertThatThrownBy(() -> adapter.getLatest("AAPL"))
        .isInstanceOf(TwelveDataAuthException.class)
        .hasMessageContaining("invalida o expirada")
        .hasMessageContaining("403");
  }

  @Test
  void shouldThrowTwelveDataRateLimitExceptionOnHttp429() {
    server.enqueue(new MockResponse().setResponseCode(429).setBody("Too Many Requests"));

    assertThatThrownBy(() -> adapter.getLatest("AAPL"))
        .isInstanceOf(TwelveDataRateLimitException.class);
  }

  @Test
  void shouldThrowTwelveDataInvalidSymbolExceptionOnHttp404() {
    server.enqueue(new MockResponse().setResponseCode(404));

    assertThatThrownBy(() -> adapter.getLatest("INVALIDO"))
        .isInstanceOf(TwelveDataInvalidSymbolException.class);
  }

  @Test
  void shouldThrowTwelveDataExceptionOnHttp400() {
    server.enqueue(new MockResponse().setResponseCode(400).setBody("Bad Request"));

    assertThatThrownBy(() -> adapter.getLatest("AAPL"))
        .isInstanceOf(TwelveDataException.class)
        .isNotInstanceOf(TwelveDataAuthException.class);
  }

  @Test
  void shouldThrowTwelveDataExceptionOnHttp500() {
    server.enqueue(new MockResponse().setResponseCode(500).setBody("Internal Server Error"));

    assertThatThrownBy(() -> adapter.getLatest("AAPL"))
        .isInstanceOf(TwelveDataException.class)
        .isNotInstanceOf(TwelveDataAuthException.class);
  }

  @Test
  void shouldThrowTwelveDataExceptionOnHttp503() {
    server.enqueue(new MockResponse().setResponseCode(503).setBody("Service Unavailable"));

    assertThatThrownBy(() -> adapter.getLatest("AAPL"))
        .isInstanceOf(TwelveDataException.class)
        .isNotInstanceOf(TwelveDataAuthException.class);
  }

  // -------------------------------------------------------------------------
  // Errores de red / timeout
  // -------------------------------------------------------------------------

  @Test
  void shouldThrowTwelveDataExceptionOnNetworkDisconnect() {
    server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));

    assertThatThrownBy(() -> adapter.getLatest("AAPL")).isInstanceOf(TwelveDataException.class);
  }

  // -------------------------------------------------------------------------
  // Jerarquía de excepciones — garantías del orquestador
  // -------------------------------------------------------------------------

  @Test
  void authExceptionShouldBeSubtypeOfTwelveDataException() {
    assertThat(TwelveDataAuthException.class).hasSuperclass(TwelveDataException.class);
  }

  @Test
  void rateLimitExceptionShouldAllowFallbackInOrchestrator() {
    // El orquestador hace fallback para ExternalProviderRateLimitException y
    // ExternalProviderUpstreamException, pero detiene el fallback ante
    // ExternalProviderInvalidSymbolException.
    // Verificamos que TwelveDataRateLimitException y TwelveDataAuthException
    // sean reconocidos como recuperables (NO InvalidSymbol).
    assertThat(
            com.mx.cryptomonitor.marketdata.domain.exception.ExternalProviderRateLimitException
                .class
                .isAssignableFrom(TwelveDataRateLimitException.class))
        .isTrue();

    assertThat(
            com.mx.cryptomonitor.marketdata.domain.exception.ExternalProviderUpstreamException.class
                .isAssignableFrom(TwelveDataAuthException.class))
        .isTrue();

    assertThat(
            com.mx.cryptomonitor.marketdata.domain.exception.ExternalProviderInvalidSymbolException
                .class
                .isAssignableFrom(TwelveDataInvalidSymbolException.class))
        .isTrue();
  }

  // -------------------------------------------------------------------------
  // Helper
  // -------------------------------------------------------------------------

  private MockResponse jsonOk(String body) {
    return new MockResponse()
        .setResponseCode(200)
        .addHeader("Content-Type", "application/json")
        .setBody(body);
  }
}
