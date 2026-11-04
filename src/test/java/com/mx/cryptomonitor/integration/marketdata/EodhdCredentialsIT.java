package com.mx.cryptomonitor.integration.marketdata;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Valida EODHD_API_TOKEN contra la API real de EODHD ({@code GET /api/real-time/{symbol}}),
 * ADR-0016 / ADR-0002.
 *
 * <p>Este IT es el <b>gate técnico de la Fase 2</b> del ADR-0016 (Action Item #4): confirma que el
 * plan EODHD entrega <b>real-time en AMBOS mercados</b> antes de rutear posiciones por mercado:
 *
 * <ul>
 *   <li>{@code AAPL.US} — NASDAQ en USD; además expone {@code previousClose}/{@code change} para el
 *       "Day change" per-holding.
 *   <li>{@code MELIN.MX} — BMV/SIC en <b>MXN nativo</b>; es el gate que habilita (o bloquea) valuar
 *       posiciones SIC sin el proxy NASDAQ×FX.
 * </ul>
 *
 * <p>Si el plan NO cubriera {@code .MX} en real-time (HTTP 401/403, {@code close=NA}, o un {@code
 * timestamp} rezagado = solo EOD), el leg MX falla con mensaje accionable y databursatil queda como
 * fuente MXN de respaldo de la Fase 2 ([[ADR-0016]] Option B).
 *
 * <p>Sufijo IT: requiere red + token real; se omite (no falla) si falta {@code EODHD_API_TOKEN}.
 * Replica el request de {@code EodhdAdapter.getLatest} y su contrato: objeto JSON con {@code
 * close}. Token invalido -> HTTP 401/403.
 */
class EodhdCredentialsIT {

  private static final String BASE_URL_DEFAULT = "https://eodhd.com";

  /**
   * Tolerancia de frescura del quote. Un real-time legitimo apunta al ultimo tick / cierre del dia
   * de mercado; 7 dias absorbe fines de semana largos y feriados de NYSE/BMV sin dejar pasar un
   * valor EOD rezagado (que delataria que el plan no cubre real-time para ese mercado).
   */
  private static final Duration MAX_STALENESS = Duration.ofDays(7);

  @Test
  void realTimeGateUsExposesDayChangeContract() {
    Map<String, Object> body = fetchRealTime("AAPL.US");
    assertRealTimeQuote("AAPL.US", body);

    assertThat(body.get("previousClose"))
        .as(
            "EODHD /api/real-time/AAPL.US sin 'previousClose': el Day change per-holding del ADR-0016"
                + " depende de este campo. Detalle: "
                + body)
        .isNotNull();
    assertThat(body.get("change"))
        .as(
            "EODHD /api/real-time/AAPL.US sin 'change': el Day change per-holding del ADR-0016"
                + " depende de este campo. Detalle: "
                + body)
        .isNotNull();
  }

  @Test
  void realTimeGateMxConfirmsNativeMxnCoverage() {
    Map<String, Object> body = fetchRealTime("MELIN.MX");
    assertRealTimeQuote("MELIN.MX", body);
  }

  private Map<String, Object> fetchRealTime(String symbol) {
    String apiToken = System.getenv("EODHD_API_TOKEN");
    Assumptions.assumeTrue(
        StringUtils.hasText(apiToken),
        "EODHD_API_TOKEN no esta configurado en el entorno, se omite la validacion contra la API"
            + " real");
    String baseUrl = System.getenv().getOrDefault("EODHD_BASE_URL", BASE_URL_DEFAULT);

    WebClient webClient = WebClient.builder().baseUrl(baseUrl).build();

    try {
      return webClient
          .get()
          .uri(
              uriBuilder ->
                  uriBuilder
                      .path("/api/real-time/" + symbol)
                      .queryParam("api_token", apiToken)
                      .queryParam("fmt", "json")
                      .build())
          .retrieve()
          .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
          .timeout(Duration.ofSeconds(10))
          .block();
    } catch (WebClientResponseException ex) {
      HttpStatusCode status = ex.getStatusCode();
      if (status.value() == 401 || status.value() == 403) {
        return fail(
            "EODHD rechazo EODHD_API_TOKEN con HTTP "
                + status.value()
                + " para /api/real-time/"
                + symbol
                + ": token invalido/revocado, o el plan/licencia NO cubre real-time para este"
                + " mercado (gate de la Fase 2).",
            ex);
      }
      return fail(
          "EODHD respondio HTTP "
              + status.value()
              + " para /api/real-time/"
              + symbol
              + " con un token en teoria valido. Causa: "
              + ex.getMessage(),
          ex);
    } catch (RuntimeException ex) {
      return fail(
          "No se pudo contactar a EODHD (" + baseUrl + ") para " + symbol + ": " + ex.getMessage(),
          ex);
    }
  }

  private void assertRealTimeQuote(String symbol, Map<String, Object> body) {
    assertThat(body)
        .as("EODHD /api/real-time/" + symbol + " no devolvio body con un token en teoria valido")
        .isNotNull();

    Object close = body.get("close");
    assertThat(close)
        .as(
            "EODHD /api/real-time/"
                + symbol
                + " respondio sin 'close' con un token en teoria valido: token invalido, cuota"
                + " agotada o contrato cambiado. Detalle: "
                + body)
        .isNotNull();

    String rawClose = String.valueOf(close).trim();
    assertThat(rawClose)
        .as(
            "EODHD /api/real-time/"
                + symbol
                + " devolvio close='"
                + rawClose
                + "': el plan no cubre este mercado en real-time o el simbolo no cotiza. Detalle: "
                + body)
        .isNotIn("NA", "null", "");

    BigDecimal price = new BigDecimal(rawClose);
    assertThat(price.signum())
        .as("EODHD /api/real-time/" + symbol + " devolvio close no positivo: " + rawClose)
        .isPositive();

    Object timestamp = body.get("timestamp");
    assertThat(timestamp)
        .as(
            "EODHD /api/real-time/"
                + symbol
                + " sin 'timestamp': no se puede confirmar que sea real-time (no EOD). Detalle: "
                + body)
        .isNotNull();

    Instant quoteTime = Instant.ofEpochSecond(Long.parseLong(String.valueOf(timestamp).trim()));
    assertThat(quoteTime)
        .as(
            "EODHD /api/real-time/"
                + symbol
                + " devolvio un quote rezagado ("
                + quoteTime
                + "): el plan parece entregar solo EOD para este mercado, no real-time (gate de la"
                + " Fase 2 NO superado).")
        .isAfter(Instant.now().minus(MAX_STALENESS));
  }
}
