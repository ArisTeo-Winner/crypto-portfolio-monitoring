package com.mx.cryptomonitor.portfolio.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;

class ChartResolutionStrategyTest {

  private final ChartResolutionStrategy strategy = new ChartResolutionStrategy();

  @Test
  void twentyFourHours_producesAtMostFiveMinuteInterval() {
    Instant end = Instant.now();
    Instant start = end.minus(1, ChronoUnit.DAYS);

    ChartResolution resolution = strategy.resolve(start, end);

    assertThat(resolution.interval().toMinutes()).isLessThanOrEqualTo(5);
    assertThat(resolution.expectedPoints()).isLessThanOrEqualTo(ChartResolutionStrategy.MAX_POINTS);
  }

  @Test
  void ninetyDays_producesAtMostEightHourInterval() {
    Instant end = Instant.now();
    Instant start = end.minus(90, ChronoUnit.DAYS);

    ChartResolution resolution = strategy.resolve(start, end);

    assertThat(resolution.interval().toHours()).isLessThanOrEqualTo(8);
    assertThat(resolution.expectedPoints()).isLessThanOrEqualTo(ChartResolutionStrategy.MAX_POINTS);
  }

  @Test
  void oneYear_producesDailyInterval() {
    Instant end = Instant.now();
    Instant start = end.minus(365, ChronoUnit.DAYS);

    ChartResolution resolution = strategy.resolve(start, end);

    assertThat(resolution.providerIntervalCode()).isEqualTo("1d");
    assertThat(resolution.expectedPoints()).isLessThanOrEqualTo(ChartResolutionStrategy.MAX_POINTS);
  }

  @Test
  void fiveYears_producesDailyInterval() {
    Instant end = Instant.now();
    Instant start = end.minus(365 * 5L, ChronoUnit.DAYS);

    ChartResolution resolution = strategy.resolve(start, end);

    assertThat(resolution.providerIntervalCode()).isEqualTo("1d");
    assertThat(resolution.expectedPoints()).isLessThanOrEqualTo(ChartResolutionStrategy.MAX_POINTS);
  }

  @Test
  void expectedPointsNeverExceedsMaximum() {
    Instant end = Instant.now();
    for (int days : new int[]{1, 7, 30, 90, 180, 365, 730, 1825}) {
      Instant start = end.minus(days, ChronoUnit.DAYS);
      ChartResolution resolution = strategy.resolve(start, end);
      assertThat(resolution.expectedPoints())
          .as("days=%d", days)
          .isLessThanOrEqualTo(ChartResolutionStrategy.MAX_POINTS);
    }
  }

  @Test
  void startEqualsEnd_doesNotThrow() {
    Instant now = Instant.now();
    ChartResolution resolution = strategy.resolve(now, now);
    assertThat(resolution).isNotNull();
    assertThat(resolution.expectedPoints()).isGreaterThanOrEqualTo(0);
  }

  @Test
  void toAggregationResolution_dailyIntervalMapsToDaily() {
    Instant end = Instant.now();
    Instant start = end.minus(365, ChronoUnit.DAYS);

    ChartResolution resolution = strategy.resolve(start, end);

    assertThat(resolution.toAggregationResolution()).isEqualTo(Resolution.DAILY);
  }

  @Test
  void toAggregationResolution_subDayIntervalMapsToIntraday() {
    Instant end = Instant.now();
    Instant start = end.minus(1, ChronoUnit.DAYS);

    ChartResolution resolution = strategy.resolve(start, end);

    assertThat(resolution.toAggregationResolution()).isEqualTo(Resolution.INTRADAY);
  }
}
