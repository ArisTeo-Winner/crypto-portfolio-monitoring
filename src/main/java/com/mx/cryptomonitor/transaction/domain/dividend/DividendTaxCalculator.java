package com.mx.cryptomonitor.transaction.domain.dividend;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Calcula la cadena fiscal de un dividendo en efectivo (ADR-0014 F3c). Para una seguridad
 * extranjera (US) en una cuenta mexicana la retencion es en cadena: primero el pais de origen
 * (W-8BEN, 10%), luego Mexico retiene su 10% sobre el ingreso <b>ya neto</b> de la retencion de
 * origen.
 *
 * <pre>
 *   gross         = cantidad x DPS
 *   sourceWithheld = gross x sourceRate
 *   afterSource    = gross - sourceWithheld
 *   localWithheld  = afterSource x localRate
 *   net            = gross - sourceWithheld - localWithheld
 * </pre>
 *
 * <p>Ejemplo GOOGL $0.20/accion, 1 accion: source=0.02, afterSource=0.18, local=0.018, net=0.162.
 *
 * <p>Las tasas vienen de {@link DividendTaxRates} (externalizadas desde {@code dividend.tax.*}); el
 * constructor sin argumentos usa las estandar. El neto se calcula como residuo exacto para
 * preservar la invariante {@code source + local + net == gross}.
 */
public final class DividendTaxCalculator {

  private static final int MONEY_SCALE = 8;

  private final DividendTaxRates rates;

  public DividendTaxCalculator() {
    this(DividendTaxRates.standard());
  }

  public DividendTaxCalculator(DividendTaxRates rates) {
    this.rates = rates != null ? rates : DividendTaxRates.standard();
  }

  /** Dividendo de una seguridad extranjera (US) en cuenta mexicana: retencion US seguida de MX. */
  public DividendTaxBreakdown foreignCashDividend(
      BigDecimal quantity, BigDecimal dividendPerShare) {
    BigDecimal gross = money(amount(quantity).multiply(amount(dividendPerShare)));
    BigDecimal sourceWithheld = money(gross.multiply(rates.sourceWithholdingRate()));
    BigDecimal afterSource = gross.subtract(sourceWithheld);
    BigDecimal localWithheld = money(afterSource.multiply(rates.localWithholdingRate()));
    BigDecimal totalWithheld = sourceWithheld.add(localWithheld);
    BigDecimal net = gross.subtract(totalWithheld);
    return new DividendTaxBreakdown(gross, sourceWithheld, localWithheld, totalWithheld, net);
  }

  private BigDecimal money(BigDecimal value) {
    return amount(value).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
  }

  private BigDecimal amount(BigDecimal value) {
    return value != null ? value : BigDecimal.ZERO;
  }
}
