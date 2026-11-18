package com.mx.cryptomonitor.marketdata.application.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import com.mx.cryptomonitor.marketdata.application.port.out.MarketStateCachePort;
import com.mx.cryptomonitor.marketdata.application.port.out.RemoteMarketStatePort;
import com.mx.cryptomonitor.marketdata.domain.model.ClosedWindow;
import com.mx.cryptomonitor.marketdata.domain.model.Market;
import com.mx.cryptomonitor.marketdata.domain.model.MarketStatusSnapshot;
import com.mx.cryptomonitor.marketdata.domain.model.RemoteMarketState;
import com.mx.cryptomonitor.marketdata.domain.model.RemoteMarketState.Status;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * Decide si un precio puede retenerse hasta la próxima apertura (ADR-0033, reglas R1 a R6).
 *
 * <p>Solo se retiene cuando todas las fuentes disponibles dicen "cerrado": el calendario local
 * manda, y la fuente remota solo puede acortar la retención o impedirla. Ante un desacuerdo gana la
 * opción que consulta de más, porque llamar a proveedores con el mercado cerrado es inofensivo y
 * congelar un precio durante una sesión real no lo es.
 */
@Slf4j
public class ClosedMarketQuotePolicy {

  private static final Duration DIVERGENCE_TOLERANCE = Duration.ofMinutes(5);
  private static final String DIVERGENCE_METRIC = "marketdata.market-state.divergence";

  private final TradingCalendarService calendar;
  private final RemoteMarketStatePort remoteState;
  private final MarketStateCachePort stateCache;
  private final Clock clock;
  private final Duration settleDelay;
  private final Duration maxHold;
  private final Duration retryInterval;
  private final MeterRegistry meterRegistry;

  /**
   * @param remoteState fuente remota; {@code null} deja la decisión solo al calendario local
   * @param stateCache caché del estado remoto; obligatorio cuando {@code remoteState} no es null
   */
  public ClosedMarketQuotePolicy(
      TradingCalendarService calendar,
      RemoteMarketStatePort remoteState,
      MarketStateCachePort stateCache,
      Clock clock,
      Duration settleDelay,
      Duration maxHold,
      Duration retryInterval,
      MeterRegistry meterRegistry) {
    this.calendar = calendar;
    this.remoteState = remoteState;
    this.stateCache = stateCache;
    this.clock = clock;
    this.settleDelay = settleDelay;
    this.maxHold = maxHold;
    this.retryInterval = retryInterval;
    this.meterRegistry = meterRegistry;
  }

  /** Periodo de cierre vigente, o vacío si el precio no debe retenerse ahora. */
  public Optional<ClosedWindow> closedWindow(Market market) {
    Instant now = clock.instant();
    MarketStatusSnapshot local = calendar.statusAt(market, now);
    if (local.isOpen()) {
      return Optional.empty();
    }

    Instant opensAt = local.nextChangeAt();
    RemoteMarketState remote = remoteStateOf(market, now, opensAt);
    if (remote.status() == Status.OPEN) {
      return Optional.empty();
    }
    if (remote.status() == Status.CLOSED && remote.opensAt().isBefore(opensAt)) {
      opensAt = remote.opensAt();
    }

    Duration hold = Duration.between(now, opensAt);
    if (hold.isZero() || hold.isNegative() || hold.compareTo(maxHold) > 0) {
      return Optional.empty();
    }

    MarketStatusSnapshot earlier = calendar.statusAt(market, now.minus(settleDelay));
    boolean settled = !earlier.isOpen() && earlier.nextChangeAt().equals(local.nextChangeAt());
    return Optional.of(new ClosedWindow(lastSessionDate(market, now, local), opensAt, settled));
  }

  private RemoteMarketState remoteStateOf(Market market, Instant now, Instant localOpensAt) {
    if (remoteState == null) {
      return RemoteMarketState.unavailable();
    }
    Optional<RemoteMarketState> cached = stateCache.find(market);
    if (cached.isPresent()) {
      return cached.get();
    }

    RemoteMarketState fetched =
        remoteState
            .fetch(market)
            .filter(state -> state.status() != Status.CLOSED || state.opensAt().isAfter(now))
            .orElseGet(RemoteMarketState::unavailable);
    Instant expiresAt =
        fetched.status() == Status.CLOSED ? fetched.opensAt() : now.plus(retryInterval);
    stateCache.save(market, fetched, expiresAt);
    reportDivergence(market, fetched, localOpensAt);
    return fetched;
  }

  private void reportDivergence(Market market, RemoteMarketState remote, Instant localOpensAt) {
    if (remote.status() == Status.OPEN) {
      log.warn(
          "Calendario divergente para {}: el calendario local dice cerrado y la fuente remota dice"
              + " abierto. No se retiene el precio; revisar market_holiday.",
          market);
      meterRegistry.counter(DIVERGENCE_METRIC, "market", market.name(), "kind", "open").increment();
      return;
    }
    if (remote.status() == Status.CLOSED
        && Duration.between(localOpensAt, remote.opensAt()).abs().compareTo(DIVERGENCE_TOLERANCE)
            > 0) {
      log.warn(
          "Calendario divergente para {}: apertura local {} y remota {}. Se usa la mas proxima;"
              + " revisar market_holiday.",
          market,
          localOpensAt,
          remote.opensAt());
      meterRegistry
          .counter(DIVERGENCE_METRIC, "market", market.name(), "kind", "opens-at")
          .increment();
    }
  }

  private LocalDate lastSessionDate(Market market, Instant now, MarketStatusSnapshot local) {
    LocalDate today = now.atZone(market.zone()).toLocalDate();
    LocalDate nextOpenDate = local.nextChangeAt().atZone(market.zone()).toLocalDate();
    // Si la proxima apertura es hoy, la sesion de hoy aun no empieza: la ultima terminada es
    // previa.
    LocalDate candidate = nextOpenDate.equals(today) ? today.minusDays(1) : today;
    while (!calendar.isTradingDay(market, candidate)) {
      candidate = candidate.minusDays(1);
    }
    return candidate;
  }
}
