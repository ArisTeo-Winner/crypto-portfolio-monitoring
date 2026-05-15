package com.mx.cryptomonitor.portfolio.domain.model;

import java.time.Duration;
import java.time.Instant;

public class ChartResolutionStrategy {

  public static final int TARGET_POINTS = 800;
  public static final long MAX_POINTS = 1500;

  public ChartResolution resolve(Instant start, Instant end) {
    long durationSeconds = end.getEpochSecond() - start.getEpochSecond();
    if (durationSeconds <= 0) {
      durationSeconds = Duration.ofDays(1).getSeconds();
    }
    long intervalSeconds = durationSeconds / TARGET_POINTS;

    String code;
    Duration interval;
    if (intervalSeconds <= 60) {
      code = "1m";
      interval = Duration.ofMinutes(1);
    } else if (intervalSeconds <= 300) {
      code = "5m";
      interval = Duration.ofMinutes(5);
    } else if (intervalSeconds <= 900) {
      code = "15m";
      interval = Duration.ofMinutes(15);
    } else if (intervalSeconds <= 3600) {
      code = "1h";
      interval = Duration.ofHours(1);
    } else if (intervalSeconds <= 14400) {
      code = "4h";
      interval = Duration.ofHours(4);
    } else if (intervalSeconds <= 28800) {
      code = "8h";
      interval = Duration.ofHours(8);
    } else {
      code = "1d";
      interval = Duration.ofDays(1);
    }

    long expectedPoints = Math.min(durationSeconds / interval.getSeconds(), MAX_POINTS);
    return new ChartResolution(start, end, interval, code, expectedPoints);
  }
}
