package com.mx.cryptomonitor.portfolio.domain.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import com.mx.cryptomonitor.portfolio.domain.model.PortfolioLot;

public class FifoEngine {

  private static final int COST_SCALE = 8;
  private static final int MONEY_SCALE = 2;

  private final Deque<PortfolioLot> lots = new ArrayDeque<>();

  public void buy(BigDecimal quantity, BigDecimal unitCost) {
    BigDecimal normalizedQuantity = amount(quantity);
    if (normalizedQuantity.compareTo(BigDecimal.ZERO) <= 0) {
      return;
    }
    lots.addLast(new PortfolioLot(normalizedQuantity, amount(unitCost)));
  }

  public BigDecimal sell(BigDecimal quantity, BigDecimal sellPrice, BigDecimal fee) {
    BigDecimal remaining = amount(quantity);
    BigDecimal proceeds = remaining.multiply(amount(sellPrice));
    BigDecimal costBasis = BigDecimal.ZERO;

    while (remaining.compareTo(BigDecimal.ZERO) > 0 && !lots.isEmpty()) {
      PortfolioLot lot = lots.removeFirst();
      BigDecimal consumed = min(remaining, lot.quantity());
      costBasis = costBasis.add(consumed.multiply(lot.unitCost()));
      remaining = remaining.subtract(consumed);

      BigDecimal leftover = lot.quantity().subtract(consumed);
      if (leftover.compareTo(BigDecimal.ZERO) > 0) {
        lots.addFirst(new PortfolioLot(leftover, lot.unitCost()));
      }
    }

    return scaleMoney(proceeds.subtract(costBasis).subtract(amount(fee)));
  }

  public BigDecimal holdings() {
    return lots.stream().map(PortfolioLot::quantity).reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  public BigDecimal openCostBasis() {
    return lots.stream()
        .map(lot -> lot.quantity().multiply(lot.unitCost()))
        .reduce(BigDecimal.ZERO, BigDecimal::add)
        .setScale(COST_SCALE, RoundingMode.HALF_UP);
  }

  public List<PortfolioLot> lots() {
    return new ArrayList<>(lots);
  }

  private BigDecimal min(BigDecimal left, BigDecimal right) {
    return left.compareTo(right) <= 0 ? left : right;
  }

  private BigDecimal amount(BigDecimal value) {
    return value != null ? value : BigDecimal.ZERO;
  }

  private BigDecimal scaleMoney(BigDecimal value) {
    return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
  }
}
