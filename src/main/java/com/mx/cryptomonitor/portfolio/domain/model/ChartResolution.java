package com.mx.cryptomonitor.portfolio.domain.model;

import java.time.Duration;
import java.time.Instant;

public record ChartResolution(
    Instant start,
    Instant end,
    Duration interval,
    String providerIntervalCode,
    long expectedPoints) {

  public Resolution toAggregationResolution() {
    return interval.toDays() >= 1 ? Resolution.DAILY : Resolution.INTRADAY;
  }
}
