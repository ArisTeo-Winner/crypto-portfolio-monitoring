package com.mx.cryptomonitor.marketdata.domain.dividend;

import java.math.BigDecimal;
import java.util.List;

/**
 * Crecimiento del dividendo de una seguridad (ADR-0014 F5). {@code annualDividends} es el DPS total
 * por año completo (solo recurring, ordenado ascendente); los {@code cagr*} son la tasa compuesta
 * de crecimiento anual por horizonte, como fraccion (0.0734 = 7.34%), o {@code null} si no hay
 * historia suficiente para ese horizonte.
 */
public record DividendGrowthResult(
    String symbol,
    List<AnnualDividend> annualDividends,
    BigDecimal cagr1y,
    BigDecimal cagr3y,
    BigDecimal cagr5y,
    BigDecimal cagr10y) {

  public record AnnualDividend(int year, BigDecimal totalDps) {}

  public static DividendGrowthResult empty(String symbol) {
    return new DividendGrowthResult(symbol, List.of(), null, null, null, null);
  }
}
