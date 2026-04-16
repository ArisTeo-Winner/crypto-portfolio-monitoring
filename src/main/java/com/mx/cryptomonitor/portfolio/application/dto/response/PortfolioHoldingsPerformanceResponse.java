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
    LocalDate firstTransactionDate) {

  public record SeriesPoint(long time, BigDecimal value) {}
}
