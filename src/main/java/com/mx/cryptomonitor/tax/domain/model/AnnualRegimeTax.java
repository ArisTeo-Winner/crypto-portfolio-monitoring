package com.mx.cryptomonitor.tax.domain.model;

import java.math.BigDecimal;

/**
 * Resultado fiscal anual de un régimen (ADR-0017 F1). Todas las cifras en MXN.
 *
 * <ul>
 *   <li>{@code grossGain} / {@code grossLoss}: suma de ventas ganadoras y perdedoras del año.
 *   <li>{@code netResult}: {@code grossGain − grossLoss} (puede ser negativo).
 *   <li>{@code taxableBase}: {@code max(netResult, 0)} — base sobre la que aplica la tasa.
 *   <li>{@code lossCarryforward}: {@code max(−netResult, 0)} — pérdida amortizable del año
 *       (calculada; su persistencia multi-año —hasta 10 años, Art. 129— es del siguiente chunk).
 *   <li>{@code cedularRate} / {@code estimatedTax}: nulos cuando el régimen aún no define tasa en
 *       esta fase (US_EQUITY: acreditamiento en F2).
 * </ul>
 */
public record AnnualRegimeTax(
    int fiscalYear,
    TaxRegime regime,
    BigDecimal grossGain,
    BigDecimal grossLoss,
    BigDecimal netResult,
    BigDecimal taxableBase,
    BigDecimal lossCarryforward,
    BigDecimal cedularRate,
    BigDecimal estimatedTax) {}
