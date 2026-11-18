package com.mx.cryptomonitor.unit.marketdata.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mx.cryptomonitor.marketdata.application.port.out.MarketStateCachePort;
import com.mx.cryptomonitor.marketdata.application.port.out.RemoteMarketStatePort;
import com.mx.cryptomonitor.marketdata.application.service.ClosedMarketQuotePolicy;
import com.mx.cryptomonitor.marketdata.application.service.TradingCalendarService;
import com.mx.cryptomonitor.marketdata.domain.model.ClosedWindow;
import com.mx.cryptomonitor.marketdata.domain.model.Market;
import com.mx.cryptomonitor.marketdata.domain.model.MarketHolidayEntity;
import com.mx.cryptomonitor.marketdata.domain.model.RemoteMarketState;
import com.mx.cryptomonitor.marketdata.domain.repository.MarketHolidayRepository;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

/**
 * Regla de retención de ADR-0033. Noviembre de 2026 (EST, UTC-5): la sesión regular de NYSE va de
 * 14:30Z a 21:00Z. El viernes 13 es hábil, el jueves 26 es festivo y el viernes 27 cierra a las
 * 13:00 ET.
 */
@ExtendWith(MockitoExtension.class)
class ClosedMarketQuotePolicyTest {

  private static final Duration SETTLE_DELAY = Duration.ofHours(4);
  private static final Duration MAX_HOLD = Duration.ofDays(5);
  private static final Duration RETRY_INTERVAL = Duration.ofHours(1);
  private static final Instant MONDAY_OPEN = Instant.parse("2026-11-16T14:30:00Z");
  private static final LocalDate FRIDAY = LocalDate.of(2026, 11, 13);

  @Mock private MarketHolidayRepository holidayRepository;
  @Mock private RemoteMarketStatePort remoteState;
  @Mock private MarketStateCachePort stateCache;

  private SimpleMeterRegistry meterRegistry;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    lenient()
        .when(holidayRepository.findByMarket("US_EQUITY"))
        .thenReturn(List.of(holiday("US_EQUITY", "2026-11-26", null), earlyClose("2026-11-27")));
    lenient()
        .when(holidayRepository.findByMarket("MX_BMV"))
        .thenReturn(List.of(holiday("MX_BMV", "2026-11-16", null)));
  }

  @Test
  void openSessionIsNeverHeldAndDoesNotSpendARemoteCall() {
    ClosedMarketQuotePolicy policy = policyAt("2026-11-13T16:00:00Z", true, MAX_HOLD);

    assertThat(policy.closedWindow(Market.US_EQUITY)).isEmpty();
    verifyNoInteractions(remoteState, stateCache);
  }

  @Test
  void rightAfterTheCloseTheWindowExistsButIsNotSettledYet() {
    ClosedMarketQuotePolicy policy = policyAt("2026-11-13T21:30:00Z", false, MAX_HOLD);

    assertThat(policy.closedWindow(Market.US_EQUITY))
        .contains(new ClosedWindow(FRIDAY, MONDAY_OPEN, false));
  }

  @Test
  void onceTheSettleDelayHasPassedTheWindowIsSettled() {
    ClosedMarketQuotePolicy policy = policyAt("2026-11-14T01:30:00Z", false, MAX_HOLD);

    assertThat(policy.closedWindow(Market.US_EQUITY))
        .contains(new ClosedWindow(FRIDAY, MONDAY_OPEN, true));
  }

  @Test
  void weekendPointsToFridaySessionAndMondayBell() {
    ClosedMarketQuotePolicy policy = policyAt("2026-11-14T15:00:00Z", false, MAX_HOLD);

    assertThat(policy.closedWindow(Market.US_EQUITY))
        .contains(new ClosedWindow(FRIDAY, MONDAY_OPEN, true));
  }

  @Test
  void beforeTheBellTheLastFinishedSessionIsThePreviousTradingDay() {
    ClosedMarketQuotePolicy policy = policyAt("2026-11-16T10:00:00Z", false, MAX_HOLD);

    assertThat(policy.closedWindow(Market.US_EQUITY))
        .contains(new ClosedWindow(FRIDAY, MONDAY_OPEN, true));
  }

  @Test
  void holidayPointsToThePreviousSessionAndTheNextTradingDayBell() {
    ClosedMarketQuotePolicy policy = policyAt("2026-11-26T17:00:00Z", false, MAX_HOLD);

    assertThat(policy.closedWindow(Market.US_EQUITY))
        .contains(
            new ClosedWindow(
                LocalDate.of(2026, 11, 25), Instant.parse("2026-11-27T14:30:00Z"), true));
  }

  @Test
  void earlyCloseDayCountsAsFinishedSessionFromItsEarlyClose() {
    // 14:00 ET del viernes 27: la sesion cerro a las 13:00 ET; cuatro horas antes estaba abierta.
    ClosedMarketQuotePolicy policy = policyAt("2026-11-27T19:00:00Z", false, MAX_HOLD);

    assertThat(policy.closedWindow(Market.US_EQUITY))
        .contains(
            new ClosedWindow(
                LocalDate.of(2026, 11, 27), Instant.parse("2026-11-30T14:30:00Z"), false));
  }

  @Test
  void bmvHolidayMondayHoldsUntilTuesdayBell() {
    ClosedMarketQuotePolicy policy = policyAt("2026-11-15T18:00:00Z", false, MAX_HOLD);

    assertThat(policy.closedWindow(Market.MX_BMV))
        .contains(new ClosedWindow(FRIDAY, Instant.parse("2026-11-17T14:30:00Z"), true));
  }

  @Test
  void holdLongerThanMaxHoldIsRejected() {
    ClosedMarketQuotePolicy policy = policyAt("2026-11-14T15:00:00Z", false, Duration.ofHours(1));

    assertThat(policy.closedWindow(Market.US_EQUITY)).isEmpty();
  }

  @Test
  void remoteSayingOpenBlocksTheHoldAndIsRememberedForTheRetryInterval() {
    Instant now = Instant.parse("2026-11-14T15:00:00Z");
    when(stateCache.find(Market.US_EQUITY)).thenReturn(Optional.empty());
    when(remoteState.fetch(Market.US_EQUITY)).thenReturn(Optional.of(RemoteMarketState.open()));
    ClosedMarketQuotePolicy policy = policyAt(now.toString(), true, MAX_HOLD);

    assertThat(policy.closedWindow(Market.US_EQUITY)).isEmpty();

    verify(stateCache).save(Market.US_EQUITY, RemoteMarketState.open(), now.plus(RETRY_INTERVAL));
    assertThat(divergences("open")).isEqualTo(1.0);
  }

  @Test
  void remoteUnavailableLeavesTheDecisionToTheLocalCalendar() {
    Instant now = Instant.parse("2026-11-14T15:00:00Z");
    when(stateCache.find(Market.US_EQUITY)).thenReturn(Optional.empty());
    when(remoteState.fetch(Market.US_EQUITY)).thenReturn(Optional.empty());
    ClosedMarketQuotePolicy policy = policyAt(now.toString(), true, MAX_HOLD);

    assertThat(policy.closedWindow(Market.US_EQUITY))
        .contains(new ClosedWindow(FRIDAY, MONDAY_OPEN, true));
    verify(stateCache)
        .save(Market.US_EQUITY, RemoteMarketState.unavailable(), now.plus(RETRY_INTERVAL));
  }

  @Test
  void whenBellsDisagreeTheEarliestWinsAndTheDivergenceIsCounted() {
    Instant remoteBell = Instant.parse("2026-11-15T14:30:00Z");
    when(stateCache.find(Market.US_EQUITY)).thenReturn(Optional.empty());
    when(remoteState.fetch(Market.US_EQUITY))
        .thenReturn(Optional.of(RemoteMarketState.closedUntil(remoteBell)));
    ClosedMarketQuotePolicy policy = policyAt("2026-11-14T15:00:00Z", true, MAX_HOLD);

    assertThat(policy.closedWindow(Market.US_EQUITY))
        .contains(new ClosedWindow(FRIDAY, remoteBell, true));
    verify(stateCache)
        .save(Market.US_EQUITY, RemoteMarketState.closedUntil(remoteBell), remoteBell);
    assertThat(divergences("opens-at")).isEqualTo(1.0);
  }

  @Test
  void remoteBellLaterThanLocalDoesNotExtendTheHold() {
    when(stateCache.find(Market.US_EQUITY)).thenReturn(Optional.empty());
    when(remoteState.fetch(Market.US_EQUITY))
        .thenReturn(
            Optional.of(RemoteMarketState.closedUntil(Instant.parse("2026-11-17T14:30:00Z"))));
    ClosedMarketQuotePolicy policy = policyAt("2026-11-14T15:00:00Z", true, MAX_HOLD);

    assertThat(policy.closedWindow(Market.US_EQUITY))
        .contains(new ClosedWindow(FRIDAY, MONDAY_OPEN, true));
  }

  @Test
  void agreeingBellsAreNotADivergence() {
    // La campana remota llega unos segundos antes por la latencia de red.
    Instant remoteBell = MONDAY_OPEN.minusSeconds(2);
    when(stateCache.find(Market.US_EQUITY)).thenReturn(Optional.empty());
    when(remoteState.fetch(Market.US_EQUITY))
        .thenReturn(Optional.of(RemoteMarketState.closedUntil(remoteBell)));
    ClosedMarketQuotePolicy policy = policyAt("2026-11-14T15:00:00Z", true, MAX_HOLD);

    assertThat(policy.closedWindow(Market.US_EQUITY))
        .contains(new ClosedWindow(FRIDAY, remoteBell, true));
    assertThat(meterRegistry.find("marketdata.market-state.divergence").counters()).isEmpty();
  }

  @Test
  void cachedRemoteStateAvoidsASecondRemoteCall() {
    when(stateCache.find(Market.US_EQUITY))
        .thenReturn(Optional.of(RemoteMarketState.closedUntil(MONDAY_OPEN)));
    ClosedMarketQuotePolicy policy = policyAt("2026-11-14T15:00:00Z", true, MAX_HOLD);

    assertThat(policy.closedWindow(Market.US_EQUITY))
        .contains(new ClosedWindow(FRIDAY, MONDAY_OPEN, true));
    verify(remoteState, never()).fetch(any());
    verify(stateCache, never()).save(any(), any(), any());
  }

  @Test
  void remoteBellAlreadyInThePastIsTreatedAsUnavailable() {
    Instant now = Instant.parse("2026-11-14T15:00:00Z");
    when(stateCache.find(Market.US_EQUITY)).thenReturn(Optional.empty());
    when(remoteState.fetch(Market.US_EQUITY))
        .thenReturn(Optional.of(RemoteMarketState.closedUntil(now.minusSeconds(1))));
    ClosedMarketQuotePolicy policy = policyAt(now.toString(), true, MAX_HOLD);

    assertThat(policy.closedWindow(Market.US_EQUITY))
        .contains(new ClosedWindow(FRIDAY, MONDAY_OPEN, true));
  }

  private ClosedMarketQuotePolicy policyAt(String now, boolean withRemote, Duration maxHold) {
    Clock clock = Clock.fixed(Instant.parse(now), ZoneOffset.UTC);
    return new ClosedMarketQuotePolicy(
        new TradingCalendarService(holidayRepository, clock),
        withRemote ? remoteState : null,
        stateCache,
        clock,
        SETTLE_DELAY,
        maxHold,
        RETRY_INTERVAL,
        meterRegistry);
  }

  private double divergences(String kind) {
    return meterRegistry
        .get("marketdata.market-state.divergence")
        .tag("kind", kind)
        .counter()
        .count();
  }

  private MarketHolidayEntity holiday(String market, String date, LocalTime earlyClose) {
    return MarketHolidayEntity.builder()
        .market(market)
        .holidayDate(LocalDate.parse(date))
        .name("H")
        .earlyClose(earlyClose)
        .build();
  }

  private MarketHolidayEntity earlyClose(String date) {
    return holiday("US_EQUITY", date, LocalTime.of(13, 0));
  }
}
