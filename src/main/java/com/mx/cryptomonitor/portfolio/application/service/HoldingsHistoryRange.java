package com.mx.cryptomonitor.portfolio.application.service;

import java.time.Duration;
import java.time.Instant;
import java.time.Period;
import java.time.ZoneOffset;
import java.util.Locale;

import com.mx.cryptomonitor.portfolio.domain.model.Resolution;

/**
 * Rango de la gráfica de historial. Tokens canónicos: {@code 1D, 1S, 1M, 3M, 6M, 1Y, ALL}. Los
 * tokens antiguos ({@code 24h, 7d, 30d, 90d, 180d, 1y, all}) se aceptan como alias durante la
 * transición del frontend y resuelven al mismo rango canónico.
 *
 * <p>El inicio de rango se calcula por calendario ({@link #startFrom(Instant)} usa {@link Period}),
 * de modo que {@code 1M/3M/6M/1Y} respetan la duración real de cada mes/año (28-31 días) en lugar
 * de un número fijo. {@code days} se conserva solo como magnitud aproximada para heurísticas de
 * tamaño de proveedor (no para calcular el inicio).
 */
public record HoldingsHistoryRange(
    String value, int days, Duration ttl, Resolution resolution, long stepSeconds, Period period) {

  public boolean isAll() {
    return days == -1;
  }

  /**
   * Inicio del rango relativo a {@code end}, por calendario. No aplica a {@code ALL}, que deriva su
   * inicio de la primera transacción del usuario; llamarlo con {@code ALL} es un error de uso.
   */
  public Instant startFrom(Instant end) {
    if (isAll()) {
      throw new IllegalStateException("ALL range has no fixed start; derive it from the holdings");
    }
    return end.atZone(ZoneOffset.UTC).minus(period).toInstant();
  }

  public static HoldingsHistoryRange parse(String range) {
    String normalized =
        range == null || range.isBlank() ? "6m" : range.trim().toLowerCase(Locale.ROOT);
    return switch (normalized) {
      case "1d", "24h" -> new HoldingsHistoryRange(
          "1D", 1, Duration.ofHours(1), Resolution.INTRADAY, 3600L, Period.ofDays(1));
      case "1s", "7d", "1w" -> new HoldingsHistoryRange(
          "1S", 7, Duration.ofHours(6), Resolution.DAILY, 86400L, Period.ofWeeks(1));
      case "1m", "30d" -> new HoldingsHistoryRange(
          "1M", 30, Duration.ofHours(6), Resolution.DAILY, 86400L, Period.ofMonths(1));
      case "3m", "90d" -> new HoldingsHistoryRange(
          "3M", 90, Duration.ofHours(6), Resolution.DAILY, 86400L, Period.ofMonths(3));
      case "6m", "180d" -> new HoldingsHistoryRange(
          "6M", 180, Duration.ofHours(6), Resolution.DAILY, 86400L, Period.ofMonths(6));
      case "1y", "12m", "365d" -> new HoldingsHistoryRange(
          "1Y", 365, Duration.ofHours(6), Resolution.DAILY, 86400L, Period.ofYears(1));
      case "all" -> new HoldingsHistoryRange(
          "ALL", -1, Duration.ofHours(24), Resolution.DAILY, 86400L, null);
      default -> throw new IllegalArgumentException(
          "range must be one of 1D, 1S, 1M, 3M, 6M, 1Y, ALL");
    };
  }
}
