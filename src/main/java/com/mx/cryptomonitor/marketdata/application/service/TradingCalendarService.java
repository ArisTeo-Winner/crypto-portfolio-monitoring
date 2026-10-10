package com.mx.cryptomonitor.marketdata.application.service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.mx.cryptomonitor.marketdata.domain.model.Market;
import com.mx.cryptomonitor.marketdata.domain.model.MarketHolidayEntity;
import com.mx.cryptomonitor.marketdata.domain.model.MarketPhase;
import com.mx.cryptomonitor.marketdata.domain.model.MarketReasonCode;
import com.mx.cryptomonitor.marketdata.domain.model.MarketStatusSnapshot;
import com.mx.cryptomonitor.marketdata.domain.model.MarketStatusSnapshot.NextChangeType;
import com.mx.cryptomonitor.marketdata.domain.repository.MarketHolidayRepository;

/**
 * Motor de calendario bursátil (ADR-0012): computa fase/motivo/próximo cambio de un mercado en un
 * instante, a partir de su zona + horario de sesión + tabla {@code market_holiday}. Determinista y
 * offline (fuente de verdad). Festivos cacheados en memoria con recarga diaria.
 */
@Service
public class TradingCalendarService {

  private static final LocalTime EARLY_CLOSE_AFTER_END = LocalTime.of(17, 0);

  private final MarketHolidayRepository holidayRepository;
  private final Clock clock;

  private final Map<Market, Map<LocalDate, Holiday>> cache = new ConcurrentHashMap<>();
  private volatile LocalDate cacheDay;

  @Autowired
  public TradingCalendarService(MarketHolidayRepository holidayRepository) {
    this(holidayRepository, Clock.systemUTC());
  }

  public TradingCalendarService(MarketHolidayRepository holidayRepository, Clock clock) {
    this.holidayRepository = holidayRepository;
    this.clock = clock;
  }

  public MarketStatusSnapshot statusAt(Market market, Instant instant) {
    ZonedDateTime local = instant.atZone(market.zone());
    LocalDate date = local.toLocalDate();
    LocalTime time = local.toLocalTime();

    Holiday holiday = holidaysFor(market).get(date);
    boolean weekend =
        date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY;
    boolean fullClosure = weekend || (holiday != null && holiday.earlyClose() == null);

    if (fullClosure) {
      MarketReasonCode reason = weekend ? MarketReasonCode.WEEKEND : MarketReasonCode.HOLIDAY;
      return closed(market, reason, nextRegularOpen(market, date));
    }

    boolean earlyCloseDay = holiday != null && holiday.earlyClose() != null;
    LocalTime regOpen = market.regularOpen();
    LocalTime regClose = earlyCloseDay ? holiday.earlyClose() : market.regularClose();

    if (market.hasExtendedHours()) {
      LocalTime afterEnd = earlyCloseDay ? EARLY_CLOSE_AFTER_END : market.afterClose();
      if (time.isBefore(market.preOpen())) {
        return closed(market, MarketReasonCode.BEFORE_OPEN, instantAt(market, date, regOpen));
      }
      if (time.isBefore(regOpen)) {
        return snapshot(
            market,
            MarketPhase.PRE_MARKET,
            MarketReasonCode.PRE_MARKET,
            false,
            NextChangeType.OPEN,
            instantAt(market, date, regOpen));
      }
      if (time.isBefore(regClose)) {
        return open(market, earlyCloseDay, instantAt(market, date, regClose));
      }
      if (time.isBefore(afterEnd)) {
        return snapshot(
            market,
            MarketPhase.AFTER_HOURS,
            MarketReasonCode.AFTER_HOURS,
            false,
            NextChangeType.OPEN,
            nextRegularOpen(market, date));
      }
      return closed(market, MarketReasonCode.AFTER_CLOSE, nextRegularOpen(market, date));
    }

    if (time.isBefore(regOpen)) {
      return closed(market, MarketReasonCode.BEFORE_OPEN, instantAt(market, date, regOpen));
    }
    if (time.isBefore(regClose)) {
      return open(market, earlyCloseDay, instantAt(market, date, regClose));
    }
    return closed(market, MarketReasonCode.AFTER_CLOSE, nextRegularOpen(market, date));
  }

  public boolean isOpenNow(Market market, Instant instant) {
    return statusAt(market, instant).isOpen();
  }

  public boolean isTradingDay(Market market, LocalDate date) {
    if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
      return false;
    }
    Holiday holiday = holidaysFor(market).get(date);
    return holiday == null || holiday.earlyClose() != null;
  }

  // ---- helpers ------------------------------------------------------------

  private MarketStatusSnapshot open(Market market, boolean earlyCloseDay, Instant nextClose) {
    return snapshot(
        market,
        MarketPhase.OPEN,
        earlyCloseDay ? MarketReasonCode.EARLY_CLOSE : MarketReasonCode.REGULAR,
        true,
        NextChangeType.CLOSE,
        nextClose);
  }

  private MarketStatusSnapshot closed(Market market, MarketReasonCode reason, Instant nextOpen) {
    return snapshot(market, MarketPhase.CLOSED, reason, false, NextChangeType.OPEN, nextOpen);
  }

  private MarketStatusSnapshot snapshot(
      Market market,
      MarketPhase phase,
      MarketReasonCode reason,
      boolean isOpen,
      NextChangeType type,
      Instant at) {
    return new MarketStatusSnapshot(market, phase, reason, isOpen, type, at);
  }

  private Instant instantAt(Market market, LocalDate date, LocalTime time) {
    return ZonedDateTime.of(date, time, market.zone()).toInstant();
  }

  private Instant nextRegularOpen(Market market, LocalDate fromDate) {
    LocalDate day = fromDate.plusDays(1);
    while (!isTradingDay(market, day)) {
      day = day.plusDays(1);
    }
    return instantAt(market, day, market.regularOpen());
  }

  private Map<LocalDate, Holiday> holidaysFor(Market market) {
    LocalDate today = LocalDate.now(clock);
    if (!today.equals(cacheDay)) {
      cache.clear();
      cacheDay = today;
    }
    return cache.computeIfAbsent(market, this::loadHolidays);
  }

  private Map<LocalDate, Holiday> loadHolidays(Market market) {
    Map<LocalDate, Holiday> map = new HashMap<>();
    for (MarketHolidayEntity e : holidayRepository.findByMarket(market.name())) {
      map.put(e.getHolidayDate(), new Holiday(e.getHolidayDate(), e.getName(), e.getEarlyClose()));
    }
    return map;
  }

  private record Holiday(LocalDate date, String name, LocalTime earlyClose) {}
}
