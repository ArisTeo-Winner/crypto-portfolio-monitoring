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
    MoneyPresentation presentation,
    BigDecimal dividendIncome,
    BigDecimal totalReturn,
    BigDecimal totalReturnPercent,
    BigDecimal realizedPnl,
    BigDecimal unrealizedPnl) {

  public record SeriesPoint(long time, BigDecimal value) {}

  /** Compat (coord. front): sin desglose realizado/no-realizado => quedan null. */
  public PortfolioHoldingsPerformanceResponse(
      List<SeriesPoint> series,
      boolean isProfit,
      BigDecimal allTimeProfit,
      BigDecimal allTimeProfitPercent,
      BigDecimal costBasis,
      LocalDate firstTransactionDate,
      MoneyPresentation presentation,
      BigDecimal dividendIncome,
      BigDecimal totalReturn,
      BigDecimal totalReturnPercent) {
    this(
        series,
        isProfit,
        allTimeProfit,
        allTimeProfitPercent,
        costBasis,
        firstTransactionDate,
        presentation,
        dividendIncome,
        totalReturn,
        totalReturnPercent,
        null,
        null);
  }

  /** Compat (ADR-0014 F4): sin componentes de Total Return => quedan null. */
  public PortfolioHoldingsPerformanceResponse(
      List<SeriesPoint> series,
      boolean isProfit,
      BigDecimal allTimeProfit,
      BigDecimal allTimeProfitPercent,
      BigDecimal costBasis,
      LocalDate firstTransactionDate,
      MoneyPresentation presentation) {
    this(
        series,
        isProfit,
        allTimeProfit,
        allTimeProfitPercent,
        costBasis,
        firstTransactionDate,
        presentation,
        null,
        null,
        null,
        null,
        null);
  }

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
        null,
        null,
        null,
        null,
        null,
        null);
  }
}
