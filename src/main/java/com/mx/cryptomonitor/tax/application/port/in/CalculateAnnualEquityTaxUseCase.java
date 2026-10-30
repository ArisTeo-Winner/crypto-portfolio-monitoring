package com.mx.cryptomonitor.tax.application.port.in;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Calcula el reporte fiscal anual de acciones por régimen (ADR-0017 F1). API pública del módulo
 * {@code tax} (named interface port.in); devuelve DTOs planos, no el modelo de dominio.
 *
 * <p>Es estimación/verificación, no asesoría fiscal ni sustituto de la declaración: el reporte
 * lleva el {@code disclaimer} y debe cotejarse contra la constancia anual de GBM.
 */
public interface CalculateAnnualEquityTaxUseCase {

  AnnualEquityTaxReport calculate(UUID userId, int fiscalYear);

  /** Reporte anual: una línea por régimen presente en el año, todo en MXN. */
  record AnnualEquityTaxReport(int fiscalYear, List<RegimeTaxLine> regimes, String disclaimer) {}

  /**
   * Resultado de un régimen. {@code cedularRate}/{@code estimatedTax} son nulos cuando el régimen
   * aún no define tasa en esta fase (US_EQUITY: acreditamiento en F2).
   */
  record RegimeTaxLine(
      String regime,
      String currency,
      BigDecimal grossGain,
      BigDecimal grossLoss,
      BigDecimal netResult,
      BigDecimal taxableBase,
      BigDecimal lossCarryforward,
      BigDecimal cedularRate,
      BigDecimal estimatedTax) {}
}
