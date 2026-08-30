package com.mx.cryptomonitor.unit.transaction.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.mx.cryptomonitor.transaction.domain.friction.FrictionBreakdown;
import com.mx.cryptomonitor.transaction.domain.friction.FrictionSide;
import com.mx.cryptomonitor.transaction.domain.friction.GbmFrictionCalculator;
import com.mx.cryptomonitor.transaction.domain.friction.ReviewStatus;
import com.mx.cryptomonitor.transaction.domain.model.Transaction;

class TransactionFrictionWiringTest {

  private final GbmFrictionCalculator calculator = new GbmFrictionCalculator();

  @Test
  void applyFrictionSetsFeeEqualToTotalFrictionCostAndBreakdown() {
    FrictionBreakdown breakdown =
        calculator.mexicanEquity(
            FrictionSide.BUY,
            new BigDecimal("100"),
            new BigDecimal("15.50"),
            new BigDecimal("1554.49"));

    Transaction transaction = new Transaction();
    transaction.applyFriction(breakdown);

    assertThat(transaction.getFee()).isEqualByComparingTo("4.49");
    assertThat(transaction.getFee())
        .isEqualByComparingTo(
            transaction
                .getBrokerCommission()
                .add(transaction.getBrokerIva())
                .add(transaction.getOtherFees()));
    assertThat(transaction.getBrokerCommission()).isEqualByComparingTo("3.87");
    assertThat(transaction.getBrokerIva()).isEqualByComparingTo("0.62");
    assertThat(transaction.getOtherFees()).isEqualByComparingTo("0.00");
    assertThat(transaction.getReviewStatus()).isEqualTo(ReviewStatus.OK);
  }
}
