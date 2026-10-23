package com.mx.cryptomonitor.portfolio.application.dto.response;

import java.math.BigDecimal;

/**
 * Resumen agregado del portafolio para la vista consolidada (ADR-0014 F4 / ADR-0015 frontend): el
 * desglose Price / Income / Total Return, sin la serie temporal. {@code allTimeProfit} = plusvalía
 * (realizada + no realizada); {@code dividendIncome} = Σ dividendo neto; {@code totalReturn} =
 * allTimeProfit + dividendIncome. Los porcentajes son porcentaje (45.00 = 45%), invariantes de
 * moneda; los importes vienen en la divisa de presentación (envelope {@code presentation}).
 */
public record PortfolioSummaryResponse(
    BigDecimal costBasis,
    BigDecimal allTimeProfit,
    BigDecimal allTimeProfitPercent,
    BigDecimal dividendIncome,
    BigDecimal totalReturn,
    BigDecimal totalReturnPercent,
    BigDecimal realizedPnl,
    BigDecimal unrealizedPnl,
    MoneyPresentation presentation) {}
