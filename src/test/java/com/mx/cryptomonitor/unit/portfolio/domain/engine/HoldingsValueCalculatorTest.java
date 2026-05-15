package com.mx.cryptomonitor.unit.portfolio.domain.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.mx.cryptomonitor.portfolio.domain.engine.HoldingsValueCalculator;
import com.mx.cryptomonitor.portfolio.domain.model.PricePoint;
import com.mx.cryptomonitor.portfolio.domain.model.QuantityTimelinePoint;
import com.mx.cryptomonitor.portfolio.domain.model.TimeValuePoint;

class HoldingsValueCalculatorTest {

  @Test
  void combinesPriceSeriesWithLatestQuantityAtOrBeforePriceTime() {
    List<QuantityTimelinePoint> quantities =
        List.of(
            new QuantityTimelinePoint(Instant.parse("2026-01-01T00:00:00Z"), new BigDecimal("2")),
            new QuantityTimelinePoint(Instant.parse("2026-01-03T00:00:00Z"), new BigDecimal("1")));

    List<TimeValuePoint> values =
        new HoldingsValueCalculator()
            .calculate(
                List.of(
                    new PricePoint(Instant.parse("2026-01-01T00:00:00Z"), new BigDecimal("10.50")),
                    new PricePoint(Instant.parse("2026-01-02T00:00:00Z"), new BigDecimal("11.00")),
                    new PricePoint(Instant.parse("2026-01-03T00:00:00Z"), new BigDecimal("12.00"))),
                quantities);

    assertThat(values)
        .containsExactly(
            new TimeValuePoint(1767225600L, new BigDecimal("21.00")),
            new TimeValuePoint(1767312000L, new BigDecimal("22.00")),
            new TimeValuePoint(1767398400L, new BigDecimal("12.00")));
  }

  @Test
  void valuesBeforeFirstTransactionAreZero() {
    List<QuantityTimelinePoint> quantities =
        List.of(
            new QuantityTimelinePoint(Instant.parse("2026-01-02T00:00:00Z"), new BigDecimal("2")));

    List<TimeValuePoint> values =
        new HoldingsValueCalculator()
            .calculate(
                List.of(
                    new PricePoint(Instant.parse("2026-01-01T00:00:00Z"), new BigDecimal("10.50"))),
                quantities);

    assertThat(values).containsExactly(new TimeValuePoint(1767225600L, new BigDecimal("0.00")));
  }

  @Test
  void emptyQuantityTimelineProducesFullZeroSeries() {
    List<TimeValuePoint> values =
        new HoldingsValueCalculator()
            .calculate(
                List.of(
                    new PricePoint(Instant.parse("2026-01-01T00:00:00Z"), new BigDecimal("10.50")),
                    new PricePoint(Instant.parse("2026-01-02T00:00:00Z"), new BigDecimal("11.00"))),
                List.of());

    assertThat(values)
        .containsExactly(
            new TimeValuePoint(1767225600L, new BigDecimal("0.00")),
            new TimeValuePoint(1767312000L, new BigDecimal("0.00")));
  }
}
