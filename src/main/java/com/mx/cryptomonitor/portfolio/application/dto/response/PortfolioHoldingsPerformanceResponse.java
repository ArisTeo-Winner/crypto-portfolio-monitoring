package com.mx.cryptomonitor.portfolio.application.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PortfolioHoldingsPerformanceResponse(
    List<SeriesPoint> series,
    boolean isProfit,
    BigDecimal allTimeProfit,
    BigDecimal allTimeProfitPercent,
    BigDecimal costBasis,
    LocalDate firstTransactionDate,
    MoneyPresentation presentation) {

  public record SeriesPoint(long time, BigDecimal value) {}

  /** Compat: sin envelope de presentacion (ADR-0010) => {@code presentation=null}. */
  public PortfolioHoldingsPerformanceResponse(
      List<SeriesPoint> series,
      boolean isProfit,
      BigDecimal allTimeProfit,
      BigDecimal allTimeProfitPercent,
      BigDecimal costBasis,
      LocalDate firstTransactionDate) {
    this(
        series,
        isProfit,
        allTimeProfit,
        allTimeProfitPercent,
        costBasis,
        firstTransactionDate,
        null);
  }
}
