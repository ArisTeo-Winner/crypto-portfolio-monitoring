package com.mx.cryptomonitor.portfolio.domain.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class AcbEngine {

  private static final int COST_SCALE = 8;
  private static final int MONEY_SCALE = 2;

  private BigDecimal quantity = BigDecimal.ZERO;
  private BigDecimal openCostBasis = BigDecimal.ZERO;

  public void buy(BigDecimal buyQuantity, BigDecimal unitCost, BigDecimal fee) {
    BigDecimal normalizedQuantity = amount(buyQuantity);
    if (normalizedQuantity.compareTo(BigDecimal.ZERO) <= 0) {
      return;
    }
    quantity = quantity.add(normalizedQuantity);
    openCostBasis =
        openCostBasis.add(normalizedQuantity.multiply(amount(unitCost))).add(amount(fee));
  }

  public BigDecimal sell(BigDecimal sellQuantity, BigDecimal sellPrice, BigDecimal fee) {
    BigDecimal consumed = min(amount(sellQuantity), quantity);
    BigDecimal averageCost = averageCost();
    BigDecimal realized =
        consumed
            .multiply(amount(sellPrice))
            .subtract(consumed.multiply(averageCost))
            .subtract(amount(fee));
    quantity = quantity.subtract(consumed);
    openCostBasis = clampToZero(openCostBasis.subtract(consumed.multiply(averageCost)));
    if (quantity.compareTo(BigDecimal.ZERO) == 0) {
      openCostBasis = BigDecimal.ZERO;
    }
    return realized.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
  }

  public BigDecimal averageCost() {
    if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
      return BigDecimal.ZERO;
    }
    return openCostBasis.divide(quantity, COST_SCALE, RoundingMode.HALF_UP);
  }

  public BigDecimal holdings() {
    return quantity;
  }

  public BigDecimal openCostBasis() {
    return openCostBasis;
  }

  private BigDecimal min(BigDecimal left, BigDecimal right) {
    return left.compareTo(right) <= 0 ? left : right;
  }

  private BigDecimal amount(BigDecimal value) {
    return value != null ? value : BigDecimal.ZERO;
  }

  private BigDecimal clampToZero(BigDecimal value) {
    return value.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : value;
  }
}
