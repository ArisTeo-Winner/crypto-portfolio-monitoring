package com.mx.cryptomonitor.transaction.domain.dividend;

import java.math.BigDecimal;

/**
 * Tasas de retencion de la cadena fiscal de dividendos (ADR-0014 F3c). Value object inmutable,
 * externalizado desde configuracion ({@code dividend.tax.*}); {@link #standard()} usa las tasas por
 * defecto del caso US via W-8BEN (10%) + Mexico (10% sobre el neto despues de la retencion US).
 *
 * <p>El orden importa: Mexico retiene sobre el ingreso <b>despues</b> de la retencion de origen, no
 * sobre el bruto (doble retencion en cadena, validado contra comprobantes GBM/DriveWealth).
 */
public record DividendTaxRates(BigDecimal sourceWithholdingRate, BigDecimal localWithholdingRate) {

  public static DividendTaxRates standard() {
    return new DividendTaxRates(new BigDecimal("0.10"), new BigDecimal("0.10"));
  }
}
