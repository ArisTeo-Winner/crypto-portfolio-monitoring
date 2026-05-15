package com.mx.cryptomonitor.portfolio.application.service;

import java.time.Duration;
import java.util.Locale;

import com.mx.cryptomonitor.portfolio.domain.model.Resolution;

public record HoldingsHistoryRange(
    String value, int days, Duration ttl, Resolution resolution, long stepSeconds) {

  public boolean isAll() {
    return days == -1;
  }

  public static HoldingsHistoryRange parse(String range) {
    String normalized =
        range == null || range.isBlank() ? "180d" : range.trim().toLowerCase(Locale.ROOT);
    return switch (normalized) {
      case "1d", "24h" -> new HoldingsHistoryRange(
          "24h", 1, Duration.ofHours(1), Resolution.INTRADAY, 3600L);
      case "7d" -> new HoldingsHistoryRange("7d", 7, Duration.ofHours(6), Resolution.DAILY, 86400L);
      case "30d" -> new HoldingsHistoryRange(
          "30d", 30, Duration.ofHours(6), Resolution.DAILY, 86400L);
      case "90d" -> new HoldingsHistoryRange(
          "90d", 90, Duration.ofHours(6), Resolution.DAILY, 86400L);
      case "180d" -> new HoldingsHistoryRange(
          "180d", 180, Duration.ofHours(6), Resolution.DAILY, 86400L);
      case "1y" -> new HoldingsHistoryRange(
          "1y", 365, Duration.ofHours(6), Resolution.DAILY, 86400L);
      case "all" -> new HoldingsHistoryRange(
          "all", -1, Duration.ofHours(24), Resolution.DAILY, 86400L);
      default -> throw new IllegalArgumentException(
          "range must be one of 24h, 7d, 30d, 90d, 180d, 1y, all");
    };
  }
}
