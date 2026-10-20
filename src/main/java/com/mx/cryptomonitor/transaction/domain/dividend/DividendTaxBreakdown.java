package com.mx.cryptomonitor.transaction.domain.dividend;

import java.math.BigDecimal;

/**
 * Desglose auditable de la cadena fiscal de un dividendo (ADR-0014 F3c). Invariante garantizada por
 * {@link DividendTaxCalculator}: {@code sourceWithheld + localWithheld + netReceived ==
 * grossAmount} (el neto es el residuo exacto, sin perdida por redondeo).
 *
 * <p>{@code totalWithheld} es lo que se persiste en {@code dividend_detail.tax_withheld}; {@code
 * grossAmount} es el monto bruto del dividendo (cantidad x DPS) y queda como el valor de la
 * transaccion DIVIDEND.
 */
public record DividendTaxBreakdown(
    BigDecimal grossAmount,
    BigDecimal sourceWithheld,
    BigDecimal localWithheld,
    BigDecimal totalWithheld,
    BigDecimal netReceived) {}
