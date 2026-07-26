package com.mx.cryptomonitor.marketdata.application.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Una fila de la tabla CETES (plazo/precio/tasa), analoga a la tabla publica de cetesdirecto.
 *
 * @param precio Calculado con el plazo NOMINAL de {@code plazoDias} (no los dias reales al
 *     vencimiento del titulo vigente en la subasta) — puede diferir de cetesdirecto en 1-2
 *     centavos, mas en el plazo de 728 dias. {@code tasa} siempre es exacta (viene directo de
 *     Banxico).
 */
public record CetesRateTableEntry(
    int plazoDias, String plazoLabel, BigDecimal precio, BigDecimal tasa, LocalDate fecha) {}
