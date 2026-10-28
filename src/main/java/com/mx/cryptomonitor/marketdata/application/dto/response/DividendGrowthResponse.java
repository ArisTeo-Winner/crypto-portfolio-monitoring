package com.mx.cryptomonitor.marketdata.application.dto.response;

import java.math.BigDecimal;
import java.util.List;

/**
 * Crecimiento del dividendo de una seguridad (ADR-0014 F5). {@code annualDividends} = DPS por año
 * completo (recurring); {@code cagr*} = tasa compuesta anual por horizonte como fracción (0.0734 =
 * 7.34%), o {@code null} si no hay historia suficiente.
 */
public record DividendGrowthResponse(
    String symbol,
    List<AnnualDividendPoint> annualDividends,
    BigDecimal cagr1y,
    BigDecimal cagr3y,
    BigDecimal cagr5y,
    BigDecimal cagr10y,
    String currency) {

  public record AnnualDividendPoint(int year, BigDecimal totalDps) {}
}
