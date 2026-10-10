package com.mx.cryptomonitor.unit.marketdata.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mx.cryptomonitor.marketdata.application.service.TradingCalendarService;
import com.mx.cryptomonitor.marketdata.domain.model.Market;
import com.mx.cryptomonitor.marketdata.domain.model.MarketHolidayEntity;
import com.mx.cryptomonitor.marketdata.domain.model.MarketPhase;
import com.mx.cryptomonitor.marketdata.domain.model.MarketReasonCode;
import com.mx.cryptomonitor.marketdata.domain.model.MarketStatusSnapshot;
import com.mx.cryptomonitor.marketdata.domain.model.MarketStatusSnapshot.NextChangeType;
import com.mx.cryptomonitor.marketdata.domain.repository.MarketHolidayRepository;

@ExtendWith(MockitoExtension.class)
class TradingCalendarServiceTest {

  @Mock private MarketHolidayRepository holidayRepository;

  private TradingCalendarService service() {
    org.mockito.Mockito.lenient()
        .when(holidayRepository.findByMarket("US_EQUITY"))
        .thenReturn(usHolidays());
    org.mockito.Mockito.lenient()
        .when(holidayRepository.findByMarket("MX_BMV"))
        .thenReturn(mxHolidays());
    Clock clock = Clock.fixed(Instant.parse("2026-10-07T00:00:00Z"), ZoneOffset.UTC);
    return new TradingCalendarService(holidayRepository, clock);
  }

  @Test
  void nyseRegularSessionIsOpenWithCloseAsNextChange() {
    // 2026-10-07 11:00 ET (EDT, UTC-4) = 15:00Z, día hábil.
    MarketStatusSnapshot s =
        service().statusAt(Market.US_EQUITY, Instant.parse("2026-10-07T15:00:00Z"));

    assertThat(s.phase()).isEqualTo(MarketPhase.OPEN);
    assertThat(s.reasonCode()).isEqualTo(MarketReasonCode.REGULAR);
    assertThat(s.isOpen()).isTrue();
    assertThat(s.nextChangeType()).isEqualTo(NextChangeType.CLOSE);
    assertThat(s.nextChangeAt()).isEqualTo(Instant.parse("2026-10-07T20:00:00Z")); // 16:00 ET
  }

  @Test
  void nysePreMarket() {
    // 06:00 ET = 10:00Z.
    MarketStatusSnapshot s =
        service().statusAt(Market.US_EQUITY, Instant.parse("2026-10-07T10:00:00Z"));
    assertThat(s.phase()).isEqualTo(MarketPhase.PRE_MARKET);
    assertThat(s.isOpen()).isFalse();
    assertThat(s.nextChangeAt()).isEqualTo(Instant.parse("2026-10-07T13:30:00Z")); // 09:30 ET
  }

  @Test
  void nyseAfterHoursPointsToNextRegularOpen() {
    // 18:00 ET = 22:00Z.
    MarketStatusSnapshot s =
        service().statusAt(Market.US_EQUITY, Instant.parse("2026-10-07T22:00:00Z"));
    assertThat(s.phase()).isEqualTo(MarketPhase.AFTER_HOURS);
    assertThat(s.nextChangeType()).isEqualTo(NextChangeType.OPEN);
    assertThat(s.nextChangeAt())
        .isEqualTo(Instant.parse("2026-10-08T13:30:00Z")); // 09:30 ET mañana
  }

  @Test
  void nyseEarlyCloseDayClosesAtOnePm() {
    // 2026-11-27 (early close 13:00 ET, EST=UTC-5). 12:00 ET = 17:00Z → OPEN EARLY_CLOSE.
    MarketStatusSnapshot open =
        service().statusAt(Market.US_EQUITY, Instant.parse("2026-11-27T17:00:00Z"));
    assertThat(open.phase()).isEqualTo(MarketPhase.OPEN);
    assertThat(open.reasonCode()).isEqualTo(MarketReasonCode.EARLY_CLOSE);
    assertThat(open.nextChangeAt()).isEqualTo(Instant.parse("2026-11-27T18:00:00Z")); // 13:00 ET

    // 14:00 ET = 19:00Z → AFTER_HOURS (13:00–17:00).
    MarketStatusSnapshot after =
        service().statusAt(Market.US_EQUITY, Instant.parse("2026-11-27T19:00:00Z"));
    assertThat(after.phase()).isEqualTo(MarketPhase.AFTER_HOURS);
  }

  @Test
  void crossHolidayIndependenceDayUsClosedButBmvOpen() {
    // 2026-07-03 NYSE cerrado (observado); BMV abierto (viernes hábil).
    TradingCalendarService cal = service();
    MarketStatusSnapshot us =
        cal.statusAt(Market.US_EQUITY, Instant.parse("2026-07-03T15:00:00Z")); // 11:00 EDT
    assertThat(us.phase()).isEqualTo(MarketPhase.CLOSED);
    assertThat(us.reasonCode()).isEqualTo(MarketReasonCode.HOLIDAY);

    MarketStatusSnapshot mx =
        cal.statusAt(Market.MX_BMV, Instant.parse("2026-07-03T17:00:00Z")); // 11:00 CT
    assertThat(mx.phase()).isEqualTo(MarketPhase.OPEN);
    assertThat(mx.reasonCode()).isEqualTo(MarketReasonCode.REGULAR);
  }

  @Test
  void crossHolidayMexicanIndependenceBmvClosedButNyseOpen() {
    // 2026-09-16 BMV cerrado; NYSE abierto.
    TradingCalendarService cal = service();
    MarketStatusSnapshot mx = cal.statusAt(Market.MX_BMV, Instant.parse("2026-09-16T17:00:00Z"));
    assertThat(mx.phase()).isEqualTo(MarketPhase.CLOSED);
    assertThat(mx.reasonCode()).isEqualTo(MarketReasonCode.HOLIDAY);

    MarketStatusSnapshot us =
        cal.statusAt(Market.US_EQUITY, Instant.parse("2026-09-16T15:00:00Z")); // 11:00 EDT
    assertThat(us.phase()).isEqualTo(MarketPhase.OPEN);
  }

  @Test
  void weekendIsClosed() {
    // 2026-10-10 es sábado.
    MarketStatusSnapshot s =
        service().statusAt(Market.US_EQUITY, Instant.parse("2026-10-10T15:00:00Z"));
    assertThat(s.phase()).isEqualTo(MarketPhase.CLOSED);
    assertThat(s.reasonCode()).isEqualTo(MarketReasonCode.WEEKEND);
    assertThat(s.nextChangeType()).isEqualTo(NextChangeType.OPEN);
  }

  @Test
  void bmvRegularSession() {
    // 11:00 CT (UTC-6) = 17:00Z.
    MarketStatusSnapshot s =
        service().statusAt(Market.MX_BMV, Instant.parse("2026-10-07T17:00:00Z"));
    assertThat(s.phase()).isEqualTo(MarketPhase.OPEN);
    assertThat(s.nextChangeAt()).isEqualTo(Instant.parse("2026-10-07T21:00:00Z")); // 15:00 CT
  }

  @Test
  void isTradingDayDistinguishesFullClosureFromEarlyCloseAndWeekend() {
    TradingCalendarService cal = service();
    assertThat(cal.isTradingDay(Market.US_EQUITY, LocalDate.of(2026, 7, 3))).isFalse(); // holiday
    assertThat(cal.isTradingDay(Market.US_EQUITY, LocalDate.of(2026, 11, 27))).isTrue(); // early
    assertThat(cal.isTradingDay(Market.US_EQUITY, LocalDate.of(2026, 10, 10))).isFalse(); // sábado
    assertThat(cal.isTradingDay(Market.MX_BMV, LocalDate.of(2026, 9, 16))).isFalse();
  }

  // ---- seeds ----

  private List<MarketHolidayEntity> usHolidays() {
    return List.of(
        full("US_EQUITY", "2026-01-01"),
        full("US_EQUITY", "2026-01-19"),
        full("US_EQUITY", "2026-02-16"),
        full("US_EQUITY", "2026-04-03"),
        full("US_EQUITY", "2026-05-25"),
        full("US_EQUITY", "2026-06-19"),
        full("US_EQUITY", "2026-07-03"),
        full("US_EQUITY", "2026-09-07"),
        full("US_EQUITY", "2026-11-26"),
        full("US_EQUITY", "2026-12-25"),
        early("US_EQUITY", "2026-11-27"),
        early("US_EQUITY", "2026-12-24"));
  }

  private List<MarketHolidayEntity> mxHolidays() {
    return List.of(
        full("MX_BMV", "2026-01-01"),
        full("MX_BMV", "2026-02-02"),
        full("MX_BMV", "2026-03-16"),
        full("MX_BMV", "2026-04-02"),
        full("MX_BMV", "2026-04-03"),
        full("MX_BMV", "2026-05-01"),
        full("MX_BMV", "2026-09-16"),
        full("MX_BMV", "2026-11-16"),
        full("MX_BMV", "2026-12-25"));
  }

  private MarketHolidayEntity full(String market, String date) {
    return MarketHolidayEntity.builder()
        .market(market)
        .holidayDate(LocalDate.parse(date))
        .name("H")
        .earlyClose(null)
        .build();
  }

  private MarketHolidayEntity early(String market, String date) {
    return MarketHolidayEntity.builder()
        .market(market)
        .holidayDate(LocalDate.parse(date))
        .name("Early")
        .earlyClose(LocalTime.of(13, 0))
        .build();
  }
}
