package com.mx.cryptomonitor.transaction.domain.merger;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Calcula el efecto de una fusion sobre la posicion de un usuario (ADR-0014 F6b-2). El caso se
 * INFIERE de los terminos (sin acoplarse al enum MergerType de marketdata):
 *
 * <ul>
 *   <li>solo {@code exchangeRatio} ({@code cashPerShare} nulo/0) -&gt; ALL_STOCK: la posicion migra
 *       al sucesor conservando el costo base (carryover), sin realizar PnL.
 *   <li>solo {@code cashPerShare} ({@code exchangeRatio} nulo/0) -&gt; ALL_CASH: la posicion se
 *       cierra a efectivo y realiza PnL = efectivo - costo base.
 *   <li>ambos presentes -&gt; CASH_AND_STOCK: diferido (requiere valor justo del sucesor para
 *       repartir el costo base); lanza {@link UnsupportedOperationException}.
 * </ul>
 */
public final class MergerEffectCalculator {

  private static final int MONEY_SCALE = 8;

  public MergerEffect compute(
      BigDecimal heldQuantity,
      BigDecimal costBasis,
      BigDecimal exchangeRatio,
      BigDecimal cashPerShare) {
    BigDecimal qty = amount(heldQuantity);
    BigDecimal basis = money(costBasis);
    boolean hasStock = isPositive(exchangeRatio);
    boolean hasCash = isPositive(cashPerShare);

    if (hasStock && hasCash) {
      throw new UnsupportedOperationException(
          "Fusion hibrida (cash+stock) aun no soportada: requiere el valor justo del sucesor");
    }
    if (hasStock) {
      BigDecimal successorQty = money(qty.multiply(exchangeRatio));
      return new MergerEffect(successorQty, basis, zero(), zero());
    }
    if (hasCash) {
      BigDecimal cash = money(qty.multiply(cashPerShare));
      return new MergerEffect(zero(), zero(), cash, money(cash.subtract(basis)));
    }
    throw new IllegalArgumentException(
        "Terminos de fusion invalidos: se requiere exchangeRatio o cashPerShare");
  }

  private boolean isPositive(BigDecimal value) {
    return value != null && value.signum() > 0;
  }

  private BigDecimal money(BigDecimal value) {
    return amount(value).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
  }

  private BigDecimal zero() {
    return BigDecimal.ZERO.setScale(MONEY_SCALE);
  }

  private BigDecimal amount(BigDecimal value) {
    return value != null ? value : BigDecimal.ZERO;
  }
}
