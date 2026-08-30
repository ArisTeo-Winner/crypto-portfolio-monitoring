package com.mx.cryptomonitor.transaction.domain.friction;

import java.math.BigDecimal;

public record FrictionBreakdown(
    BigDecimal grossAmount,
    BigDecimal brokerCommission,
    BigDecimal brokerIva,
    BigDecimal otherFees,
    BigDecimal totalFrictionCost,
    BigDecimal finalNetCost,
    BigDecimal adjustedUnitPrice,
    ReviewStatus reviewStatus) {

  public BigDecimal fee() {
    return totalFrictionCost;
  }
}
