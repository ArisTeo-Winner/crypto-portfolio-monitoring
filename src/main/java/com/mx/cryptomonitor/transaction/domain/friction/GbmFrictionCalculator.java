package com.mx.cryptomonitor.transaction.domain.friction;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Normaliza la fricción de corretaje (comisión, IVA, fees) de un ticket de broker hacia un costo
 * neto auditable. No recalcula el costo base del portafolio: produce el {@code fee} (=
 * totalFrictionCost) que consumen {@code AcbEngine} y {@code TransactionRealizedPnlService}.
 *
 * <p>Regla de redondeo GBM validada contra tickets reales (ver ADR-0003): la comisión se trunca a 2
 * decimales para presentación, pero el IVA se calcula sobre la comisión de precisión completa.
 *
 * <p>Las tasas (comisión/IVA/tolerancia) vienen de {@link FrictionRates}, externalizado desde
 * configuración; el constructor sin argumentos usa las tasas estándar.
 */
public final class GbmFrictionCalculator {

  private static final int MONEY_SCALE = 2;
  private static final int UNIT_SCALE = 8;

  private final FrictionRates rates;

  public GbmFrictionCalculator() {
    this(FrictionRates.standard());
  }

  public GbmFrictionCalculator(FrictionRates rates) {
    this.rates = rates != null ? rates : FrictionRates.standard();
  }

  public FrictionBreakdown mexicanEquity(
      FrictionSide side, BigDecimal quantity, BigDecimal unitPrice, BigDecimal reportedTotal) {
    return mexicanEquity(side, quantity, unitPrice, rates.commissionRate(), reportedTotal);
  }

  public FrictionBreakdown mexicanEquity(
      FrictionSide side,
      BigDecimal quantity,
      BigDecimal unitPrice,
      BigDecimal commissionRate,
      BigDecimal reportedTotal) {
    BigDecimal rate = commissionRate != null ? commissionRate : rates.commissionRate();
    BigDecimal rawGross = amount(quantity).multiply(amount(unitPrice));
    BigDecimal commissionRaw = rawGross.multiply(rate);
    BigDecimal commission = commissionRaw.setScale(MONEY_SCALE, RoundingMode.DOWN);
    BigDecimal iva = money(commissionRaw.multiply(rates.ivaRate()));
    return build(side, quantity, money(rawGross), commission, iva, BigDecimal.ZERO, reportedTotal);
  }

  public FrictionBreakdown driveWealth(
      FrictionSide side,
      BigDecimal quantity,
      BigDecimal principalAmount,
      BigDecimal commission,
      BigDecimal transactionFee,
      BigDecimal otherFees,
      BigDecimal reportedNet) {
    BigDecimal gross = money(amount(principalAmount));
    return build(
        side,
        quantity,
        gross,
        money(amount(commission)),
        BigDecimal.ZERO.setScale(MONEY_SCALE),
        money(amount(transactionFee).add(amount(otherFees))),
        reportedNet);
  }

  private FrictionBreakdown build(
      FrictionSide side,
      BigDecimal quantity,
      BigDecimal gross,
      BigDecimal commission,
      BigDecimal iva,
      BigDecimal otherFees,
      BigDecimal reportedTotal) {
    BigDecimal fees = money(otherFees);
    BigDecimal totalFriction = money(commission.add(iva).add(fees));
    BigDecimal netCost =
        side == FrictionSide.SELL
            ? money(gross.subtract(totalFriction))
            : money(gross.add(totalFriction));
    BigDecimal adjustedUnitPrice =
        amount(quantity).signum() == 0
            ? BigDecimal.ZERO.setScale(UNIT_SCALE)
            : netCost.divide(amount(quantity), UNIT_SCALE, RoundingMode.HALF_UP);
    BigDecimal perUnitFriction =
        amount(quantity).signum() == 0
            ? BigDecimal.ZERO.setScale(UNIT_SCALE)
            : totalFriction.divide(amount(quantity), UNIT_SCALE, RoundingMode.HALF_UP);
    return new FrictionBreakdown(
        gross,
        commission,
        iva,
        fees,
        totalFriction,
        netCost,
        adjustedUnitPrice,
        perUnitFriction,
        reviewStatus(netCost, reportedTotal));
  }

  private ReviewStatus reviewStatus(BigDecimal computedTotal, BigDecimal reportedTotal) {
    if (reportedTotal == null) {
      return ReviewStatus.OK;
    }
    BigDecimal difference = computedTotal.subtract(money(reportedTotal)).abs();
    return difference.compareTo(rates.tolerance()) > 0
        ? ReviewStatus.REQUIERE_REVISION
        : ReviewStatus.OK;
  }

  private BigDecimal money(BigDecimal value) {
    return amount(value).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
  }

  private BigDecimal amount(BigDecimal value) {
    return value != null ? value : BigDecimal.ZERO;
  }
}
