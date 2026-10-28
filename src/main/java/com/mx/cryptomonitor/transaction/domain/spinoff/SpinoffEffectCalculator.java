package com.mx.cryptomonitor.transaction.domain.spinoff;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Reparte el costo base de la matriz en un spinoff (ADR-0014 F6e-2):
 *
 * <pre>
 *   spincoQuantity      = parentQuantity * sharesPerParentShare
 *   spincoBasis         = parentBasis * allocationPct
 *   retainedParentBasis = parentBasis - spincoBasis   (residuo exacto; sin PnL)
 * </pre>
 *
 * <p>{@code allocationPct} nulo => diferido ({@link UnsupportedOperationException}, como la fusión
 * híbrida: requiere el valor justo relativo al spinoff). Fuera de [0,1] => inválido.
 */
public final class SpinoffEffectCalculator {

  private static final int MONEY_SCALE = 8;

  public SpinoffEffect compute(
      BigDecimal parentQuantity,
      BigDecimal parentBasis,
      BigDecimal sharesPerParentShare,
      BigDecimal allocationPct) {
    if (allocationPct == null) {
      throw new UnsupportedOperationException(
          "Spinoff sin cost_basis_allocation_pct: reparto de base por determinar");
    }
    if (allocationPct.signum() < 0 || allocationPct.compareTo(BigDecimal.ONE) > 0) {
      throw new IllegalArgumentException("allocationPct fuera de [0,1]: " + allocationPct);
    }
    BigDecimal qty = amount(parentQuantity);
    BigDecimal basis = money(parentBasis);
    BigDecimal spincoQty = money(qty.multiply(amount(sharesPerParentShare)));
    BigDecimal spincoBasis = money(basis.multiply(allocationPct));
    BigDecimal retained = basis.subtract(spincoBasis);
    return new SpinoffEffect(retained, spincoQty, spincoBasis);
  }

  private BigDecimal money(BigDecimal value) {
    return amount(value).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
  }

  private BigDecimal amount(BigDecimal value) {
    return value != null ? value : BigDecimal.ZERO;
  }
}
