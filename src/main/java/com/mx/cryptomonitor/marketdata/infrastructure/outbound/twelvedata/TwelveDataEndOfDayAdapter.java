package com.mx.cryptomonitor.marketdata.infrastructure.outbound.twelvedata;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;

import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.mx.cryptomonitor.marketdata.application.port.out.OfficialClosePort;
import com.mx.cryptomonitor.marketdata.domain.model.StockQuote;

import lombok.extern.slf4j.Slf4j;

/**
 * Cierre de fin de día de Twelve Data: {@code GET /eod?symbol={SYM}} (1 crédito, ADR-0033). Su
 * {@code datetime} es la fecha de la sesión, lo que permite confirmar que el precio es el cierre de
 * la sesión esperada antes de retenerlo.
 *
 * <p>Si Twelve Data rechaza por cuota o credenciales (429/401/403) se deja de consultar durante
 * {@code retryInterval}: reintentar por cada símbolo solo gastaría más cuota. La API key viaja en
 * la query, por eso los mensajes de error de WebClient nunca se registran.
 */
@Slf4j
public class TwelveDataEndOfDayAdapter implements OfficialClosePort {

  static final String PROVIDER = "twelvedata-eod";

  private static final Duration TIMEOUT = Duration.ofSeconds(5);
  private static final int ISO_DATE_LENGTH = 10;

  private final WebClient webClient;
  private final String baseUrl;
  private final String apiKey;
  private final Clock clock;
  private final Duration retryInterval;

  private volatile Instant blockedUntil = Instant.MIN;

  public TwelveDataEndOfDayAdapter(
      WebClient webClient, String baseUrl, String apiKey, Clock clock, Duration retryInterval) {
    this.webClient = webClient;
    this.baseUrl = baseUrl;
    this.apiKey = apiKey;
    this.clock = clock;
    this.retryInterval = retryInterval;
  }

  @Override
  public Optional<StockQuote> find(String symbol) {
    Instant now = clock.instant();
    if (now.isBefore(blockedUntil)) {
      return Optional.empty();
    }
    try {
      Map<?, ?> body =
          webClient
              .get()
              .uri("%s/eod?symbol=%s&apikey=%s".formatted(baseUrl, symbol, apiKey))
              .retrieve()
              .bodyToMono(Map.class)
              .block(TIMEOUT);
      return toQuote(body, symbol, now);
    } catch (WebClientResponseException ex) {
      rejected(symbol, ex.getStatusCode().value(), now);
      return Optional.empty();
    } catch (RuntimeException ex) {
      log.warn("TwelveData /eod no disponible para {}: {}", symbol, ex.getClass().getSimpleName());
      return Optional.empty();
    }
  }

  private Optional<StockQuote> toQuote(Map<?, ?> body, String symbol, Instant now) {
    if (body == null) {
      return Optional.empty();
    }
    if ("error".equalsIgnoreCase(String.valueOf(body.get("status")))) {
      rejected(symbol, parseCode(body.get("code")), now);
      return Optional.empty();
    }
    Object datetime = body.get("datetime");
    Object close = body.get("close");
    if (datetime == null || close == null) {
      return Optional.empty();
    }
    String rawDate = String.valueOf(datetime).trim();
    LocalDate sessionDate =
        LocalDate.parse(
            rawDate.length() > ISO_DATE_LENGTH ? rawDate.substring(0, ISO_DATE_LENGTH) : rawDate);
    return Optional.of(
        new StockQuote(new BigDecimal(String.valueOf(close).trim()), now, PROVIDER, sessionDate));
  }

  private void rejected(String symbol, int code, Instant now) {
    if (code == 429 || code == 401 || code == 403) {
      blockedUntil = now.plus(retryInterval);
      log.warn(
          "TwelveData /eod rechazo la consulta (code {}). No se reintenta hasta {}.",
          code,
          blockedUntil);
      return;
    }
    log.info("TwelveData /eod sin cierre para {} (code {})", symbol, code);
  }

  private int parseCode(Object raw) {
    if (raw instanceof Number number) {
      return number.intValue();
    }
    try {
      return Integer.parseInt(String.valueOf(raw));
    } catch (NumberFormatException ignored) {
      return -1;
    }
  }
}
