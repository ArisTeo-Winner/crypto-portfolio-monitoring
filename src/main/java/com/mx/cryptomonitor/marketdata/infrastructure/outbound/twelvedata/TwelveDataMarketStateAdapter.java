package com.mx.cryptomonitor.marketdata.infrastructure.outbound.twelvedata;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.mx.cryptomonitor.marketdata.application.port.out.RemoteMarketStatePort;
import com.mx.cryptomonitor.marketdata.domain.model.Market;
import com.mx.cryptomonitor.marketdata.domain.model.RemoteMarketState;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Estado de mercado de Twelve Data: {@code GET /market_state?code={MIC}} (1 crédito, ADR-0033).
 *
 * <p>{@code time_to_open} es una cuenta regresiva ({@code HH:MM:SS}, con horas que pueden pasar de
 * 24) válida solo en el instante de la respuesta, así que aquí mismo se convierte en la campana de
 * apertura como instante absoluto. Se toma como base el instante previo a la petición: la campana
 * calculada queda, como mucho, la latencia de red antes de la real, nunca después.
 *
 * <p>La API key viaja en la query: los mensajes de error de WebClient incluyen la URL, por eso
 * nunca se registran.
 */
@RequiredArgsConstructor
@Slf4j
public class TwelveDataMarketStateAdapter implements RemoteMarketStatePort {

  private static final Pattern COUNTDOWN = Pattern.compile("^(\\d{1,3}):([0-5]\\d):([0-5]\\d)$");
  private static final Duration TIMEOUT = Duration.ofSeconds(5);
  private static final Map<Market, String> MIC_BY_MARKET =
      Map.of(Market.US_EQUITY, "XNYS", Market.MX_BMV, "XMEX");

  private final WebClient webClient;
  private final String baseUrl;
  private final String apiKey;
  private final Clock clock;

  @Override
  public Optional<RemoteMarketState> fetch(Market market) {
    String mic = MIC_BY_MARKET.get(market);
    Instant requestedAt = clock.instant();
    try {
      List<?> rows =
          webClient
              .get()
              .uri("%s/market_state?code=%s&apikey=%s".formatted(baseUrl, mic, apiKey))
              .retrieve()
              .bodyToMono(List.class)
              .block(TIMEOUT);
      Optional<RemoteMarketState> state = toState(rows, requestedAt);
      if (state.isEmpty()) {
        log.warn("TwelveData market_state devolvio una respuesta ilegible para {}", mic);
      }
      return state;
    } catch (RuntimeException ex) {
      log.warn("TwelveData market_state no disponible para {}: {}", mic, describe(ex));
      return Optional.empty();
    }
  }

  /** Convierte {@code HH:MM:SS} en duración; vacío si el valor no tiene ese formato. */
  public static Optional<Duration> parseCountdown(String raw) {
    if (raw == null) {
      return Optional.empty();
    }
    Matcher matcher = COUNTDOWN.matcher(raw.trim());
    if (!matcher.matches()) {
      return Optional.empty();
    }
    return Optional.of(
        Duration.ofHours(Long.parseLong(matcher.group(1)))
            .plusMinutes(Long.parseLong(matcher.group(2)))
            .plusSeconds(Long.parseLong(matcher.group(3))));
  }

  private Optional<RemoteMarketState> toState(List<?> rows, Instant requestedAt) {
    if (rows == null || rows.isEmpty() || !(rows.get(0) instanceof Map<?, ?> row)) {
      return Optional.empty();
    }
    Object open = row.get("is_market_open");
    if (open == null) {
      return Optional.empty();
    }
    if (Boolean.parseBoolean(String.valueOf(open))) {
      return Optional.of(RemoteMarketState.open());
    }
    Object timeToOpen = row.get("time_to_open");
    return parseCountdown(timeToOpen == null ? null : String.valueOf(timeToOpen))
        .filter(countdown -> !countdown.isZero())
        .map(countdown -> RemoteMarketState.closedUntil(requestedAt.plus(countdown)));
  }

  static String describe(RuntimeException ex) {
    if (ex instanceof WebClientResponseException response) {
      return "HTTP " + response.getStatusCode().value();
    }
    return ex.getClass().getSimpleName();
  }
}
