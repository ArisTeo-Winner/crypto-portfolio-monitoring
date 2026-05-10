package com.mx.cryptomonitor.portfolio.application.service;

import java.time.Duration;
import java.util.Locale;

public record HoldingsHistoryRange(String value, int days, Duration ttl) {

  public static HoldingsHistoryRange parse(String range) {
    String normalized =
        range == null || range.isBlank() ? "180d" : range.trim().toLowerCase(Locale.ROOT);
    return switch (normalized) {
      case "1d", "24h" -> new HoldingsHistoryRange("24h", 1, Duration.ofHours(1));
      case "7d" -> new HoldingsHistoryRange("7d", 7, Duration.ofHours(6));
      case "30d" -> new HoldingsHistoryRange("30d", 30, Duration.ofHours(6));
      case "90d" -> new HoldingsHistoryRange("90d", 90, Duration.ofHours(6));
      case "180d" -> new HoldingsHistoryRange("180d", 180, Duration.ofHours(6));
      case "1y" -> new HoldingsHistoryRange("1y", 365, Duration.ofHours(6));
      default ->
          throw new IllegalArgumentException(
              "range must be one of 24h, 7d, 30d, 90d, 180d, 1y");
    };
  }
}
