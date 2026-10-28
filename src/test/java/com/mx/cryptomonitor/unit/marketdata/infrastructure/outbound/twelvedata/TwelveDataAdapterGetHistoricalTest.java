package com.mx.cryptomonitor.unit.marketdata.infrastructure.outbound.twelvedata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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
 * Tests de {@link TwelveDataAdapter#getHistorical(String, LocalDate)}.
 *
 * <p>Cubre todos los escenarios del endpoint {@code /time_series} con {@code interval=1day} y
 * {@code outputsize=1}: éxito, fecha no encontrada, errores en body, errores HTTP y fallos de red.
 */
class TwelveDataAdapterGetHistoricalTest {

  private static final LocalDate DATE = LocalDate.of(2026, 5, 22);

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
  void shouldReturnClosePriceForMatchingDate() {
    server.enqueue(
        jsonOk(
            """
        {
          "meta": {"symbol": "AAPL", "interval": "1day"},
          "values": [
            {"datetime": "2026-05-22", "open": "308.0", "high": "310.0", "low": "307.0", "close": "309.50", "volume": "5000000"}
          ],
          "status": "ok"
        }
        """));

    Optional<BigDecimal> result = adapter.getHistorical("AAPL", DATE);

    assertThat(result).isPresent();
    assertThat(result.get()).isEqualByComparingTo("309.50");
  }

  @Test
  void shouldReturnEmptyWhenValuesListIsEmpty() {
    server.enqueue(jsonOk("""
        {"meta": {}, "values": [], "status": "ok"}
        """));

    assertThat(adapter.getHistorical("AAPL", DATE)).isEmpty();
  }

  @Test
  void shouldReturnEmptyWhenDateNotPresentInReturnedValues() {
    // La lista tiene velas pero ninguna coincide con la fecha pedida (ej. fin de semana)
    server.enqueue(
        jsonOk(
            """
        {
          "meta": {},
          "values": [
            {"datetime": "2026-05-21", "close": "307.00"},
            {"datetime": "2026-05-20", "close": "305.00"}
          ],
          "status": "ok"
        }
        """));

    assertThat(adapter.getHistorical("AAPL", DATE)).isEmpty();
  }

  @Test
  void shouldFindDateAnywhere_InValues_NotOnlyFirst() {
    // Twelve Data devuelve descendente; la fecha buscada puede estar en posición 2, 3...
    server.enqueue(
        jsonOk(
            """
        {
          "meta": {"symbol": "IBM"},
          "values": [
            {"datetime": "2026-05-24", "close": "260.00"},
            {"datetime": "2026-05-23", "close": "258.00"},
            {"datetime": "2026-05-22", "open": "262.0", "high": "264.4", "low": "253.4", "close": "253.84", "volume": "19025900"}
          ],
          "status": "ok"
        }
        """));

    Optional<BigDecimal> result = adapter.getHistorical("IBM", DATE);

    assertThat(result).isPresent();
    assertThat(result.get()).isEqualByComparingTo("253.84");
  }

  @Test
  void shouldReturnEmptyWhenCloseFieldIsMissing() {
    server.enqueue(
        jsonOk(
            """
        {
          "meta": {},
          "values": [
            {"datetime": "2026-05-22", "open": "308.0"}
          ],
          "status": "ok"
        }
        """));

    assertThat(adapter.getHistorical("AAPL", DATE)).isEmpty();
  }

  @Test
  void shouldReturnEmptyWhenValuesFieldIsAbsent() {
    server.enqueue(jsonOk("""
        {"meta": {}, "status": "ok"}
        """));

    assertThat(adapter.getHistorical("AAPL", DATE)).isEmpty();
  }

  @Test
  void shouldUseInterval1dayAndOutputsizeBasedOnDaysBackAndNoDateFilters() throws Exception {
    // El plan gratuito de Twelve Data no admite start_date/end_date.
    // El adapter calcula outputsize = daysBack + 10 y busca la fecha client-side.
    server.enqueue(jsonOk("""
        {"meta": {}, "values": [], "status": "ok"}
        """));

    adapter.getHistorical("AAPL", DATE);

    RecordedRequest req = server.takeRequest(1, TimeUnit.SECONDS);
    assertThat(req).isNotNull();
    String path = req.getPath();

    long daysBack = ChronoUnit.DAYS.between(DATE, LocalDate.now());
    int expectedOutputSize = (int) Math.min(Math.max(daysBack + 10, 10), 5000);

    assertThat(path)
        .contains("interval=1day")
        .contains("outputsize=" + expectedOutputSize)
        .contains("apikey=test-key")
        // NO debe usar parámetros de fecha en la URL
        .doesNotContain("start_date")
        .doesNotContain("end_date");
  }

  // -------------------------------------------------------------------------
  // Errores en body con HTTP 200
  // -------------------------------------------------------------------------

  @Test
  void shouldThrowRateLimitExceptionWhenBodyCode429() {
    server.enqueue(
        jsonOk(
            """
        {
          "code": 429,
          "message": "You have run out of API credits for the current minute.",
          "status": "error"
        }
        """));

    assertThatThrownBy(() -> adapter.getHistorical("AAPL", DATE))
        .isInstanceOf(TwelveDataRateLimitException.class)
        .hasMessageContaining("API credits");
  }

  @Test
  void shouldThrowInvalidSymbolExceptionWhenBodyCode400AndSymbolNotValid() {
    // Twelve Data: "**INVALIDO** is not valid." → símbolo no existe → detener fallback
    server.enqueue(
        jsonOk(
            """
        {
          "code": 400,
          "message": "**INVALIDO** is not valid.",
          "status": "error"
        }
        """));

    assertThatThrownBy(() -> adapter.getHistorical("INVALIDO", DATE))
        .isInstanceOf(TwelveDataInvalidSymbolException.class);
  }

  @Test
  void shouldThrowUpstreamExceptionWhenBodyCode400AndNoDataForDates() {
    // Reproducción exacta del bug en producción: IBM con fecha futura/sin datos.
    // Twelve Data devuelve code=400 pero el símbolo sí existe → se debe permitir fallback.
    server.enqueue(
        jsonOk(
            """
        {
          "code": 400,
          "message": "No data is available on the specified dates. Try setting different start/end dates.",
          "status": "error"
        }
        """));

    assertThatThrownBy(() -> adapter.getHistorical("IBM", DATE))
        .isInstanceOf(TwelveDataException.class)
        // NO debe ser InvalidSymbol — eso detendría el fallback hacia AlphaVantage
        .isNotInstanceOf(TwelveDataInvalidSymbolException.class);
  }

  @Test
  void shouldThrowInvalidSymbolExceptionWhenBodyCode404() {
    server.enqueue(
        jsonOk(
            """
        {
          "code": 404,
          "message": "Symbol not found.",
          "status": "error"
        }
        """));

    assertThatThrownBy(() -> adapter.getHistorical("XXXXX", DATE))
        .isInstanceOf(TwelveDataInvalidSymbolException.class);
  }

  @Test
  void shouldThrowAuthExceptionWhenBodyCode401() {
    server.enqueue(
        jsonOk(
            """
        {
          "code": 401,
          "message": "Your API key is invalid.",
          "status": "error"
        }
        """));

    assertThatThrownBy(() -> adapter.getHistorical("AAPL", DATE))
        .isInstanceOf(TwelveDataAuthException.class)
        .hasMessageContaining("invalida o expirada");
  }

  @Test
  void shouldThrowAuthExceptionWhenBodyCode403() {
    server.enqueue(
        jsonOk(
            """
        {
          "code": 403,
          "message": "Your API key does not have access to this resource.",
          "status": "error"
        }
        """));

    assertThatThrownBy(() -> adapter.getHistorical("AAPL", DATE))
        .isInstanceOf(TwelveDataAuthException.class)
        .hasMessageContaining("invalida o expirada");
  }

  @Test
  void shouldThrowGenericExceptionForUnknownBodyErrorCode() {
    server.enqueue(
        jsonOk(
            """
        {
          "code": 503,
          "message": "Service temporarily unavailable.",
          "status": "error"
        }
        """));

    assertThatThrownBy(() -> adapter.getHistorical("AAPL", DATE))
        .isInstanceOf(TwelveDataException.class)
        .isNotInstanceOf(TwelveDataAuthException.class)
        .isNotInstanceOf(TwelveDataRateLimitException.class)
        .isNotInstanceOf(TwelveDataInvalidSymbolException.class);
  }

  // -------------------------------------------------------------------------
  // Errores HTTP
  // -------------------------------------------------------------------------

  @Test
  void shouldThrowAuthExceptionOnHttp401() {
    server.enqueue(new MockResponse().setResponseCode(401).setBody("Unauthorized"));

    assertThatThrownBy(() -> adapter.getHistorical("AAPL", DATE))
        .isInstanceOf(TwelveDataAuthException.class)
        .hasMessageContaining("401");
  }

  @Test
  void shouldThrowAuthExceptionOnHttp403() {
    server.enqueue(new MockResponse().setResponseCode(403).setBody("Forbidden"));

    assertThatThrownBy(() -> adapter.getHistorical("AAPL", DATE))
        .isInstanceOf(TwelveDataAuthException.class)
        .hasMessageContaining("403");
  }

  @Test
  void shouldThrowRateLimitExceptionOnHttp429() {
    server.enqueue(new MockResponse().setResponseCode(429).setBody("Too Many Requests"));

    assertThatThrownBy(() -> adapter.getHistorical("AAPL", DATE))
        .isInstanceOf(TwelveDataRateLimitException.class);
  }

  @Test
  void shouldThrowInvalidSymbolExceptionOnHttp404() {
    server.enqueue(new MockResponse().setResponseCode(404));

    assertThatThrownBy(() -> adapter.getHistorical("INVALIDO", DATE))
        .isInstanceOf(TwelveDataInvalidSymbolException.class);
  }

  @Test
  void shouldThrowTwelveDataExceptionOnHttp500() {
    server.enqueue(new MockResponse().setResponseCode(500).setBody("Internal Server Error"));

    assertThatThrownBy(() -> adapter.getHistorical("AAPL", DATE))
        .isInstanceOf(TwelveDataException.class)
        .isNotInstanceOf(TwelveDataAuthException.class);
  }

  @Test
  void shouldThrowTwelveDataExceptionOnHttp503() {
    server.enqueue(new MockResponse().setResponseCode(503));

    assertThatThrownBy(() -> adapter.getHistorical("AAPL", DATE))
        .isInstanceOf(TwelveDataException.class);
  }

  // -------------------------------------------------------------------------
  // Errores de red / timeout
  // -------------------------------------------------------------------------

  @Test
  void shouldThrowTwelveDataExceptionOnNetworkDisconnect() {
    server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START));

    assertThatThrownBy(() -> adapter.getHistorical("AAPL", DATE))
        .isInstanceOf(TwelveDataException.class);
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
