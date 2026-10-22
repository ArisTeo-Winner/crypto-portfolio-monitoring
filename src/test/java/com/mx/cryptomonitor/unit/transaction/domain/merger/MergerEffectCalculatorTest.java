package com.mx.cryptomonitor.unit.transaction.domain.merger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.mx.cryptomonitor.transaction.domain.merger.MergerEffect;
import com.mx.cryptomonitor.transaction.domain.merger.MergerEffectCalculator;

class MergerEffectCalculatorTest {

  private final MergerEffectCalculator calculator = new MergerEffectCalculator();

  @Test
  void allStockMigratesByExchangeRatioConservingCostBasis() {
    // 100 CEPT con costo base 1500, ratio 1.5 -> 150 SECZ al mismo costo 1500, sin PnL.
    MergerEffect effect =
        calculator.compute(
            new BigDecimal("100"), new BigDecimal("1500"), new BigDecimal("1.5"), null);

    assertThat(effect.successorQuantity()).isEqualByComparingTo("150");
    assertThat(effect.successorCostBasis()).isEqualByComparingTo("1500");
    assertThat(effect.cashProceeds()).isEqualByComparingTo("0");
    assertThat(effect.realizedPnl()).isEqualByComparingTo("0");
  }

  @Test
  void allStockOneToOneDeSpac() {
    MergerEffect effect =
        calculator.compute(new BigDecimal("40"), new BigDecimal("800"), new BigDecimal("1"), null);

    assertThat(effect.successorQuantity()).isEqualByComparingTo("40");
    assertThat(effect.successorCostBasis()).isEqualByComparingTo("800");
  }

  @Test
  void allCashClosesPositionRealizingGain() {
    // 100 a cash 20 = 2000 proceeds, costo 1500 -> PnL +500, sin sucesor.
    MergerEffect effect =
        calculator.compute(
            new BigDecimal("100"), new BigDecimal("1500"), null, new BigDecimal("20"));

    assertThat(effect.cashProceeds()).isEqualByComparingTo("2000");
    assertThat(effect.realizedPnl()).isEqualByComparingTo("500");
    assertThat(effect.successorQuantity()).isEqualByComparingTo("0");
    assertThat(effect.successorCostBasis()).isEqualByComparingTo("0");
  }

  @Test
  void allCashCanRealizeLoss() {
    MergerEffect effect =
        calculator.compute(
            new BigDecimal("100"), new BigDecimal("2500"), null, new BigDecimal("20"));

    assertThat(effect.cashProceeds()).isEqualByComparingTo("2000");
    assertThat(effect.realizedPnl()).isEqualByComparingTo("-500");
  }

  @Test
  void hybridCashAndStockIsDeferred() {
    assertThatThrownBy(
            () ->
                calculator.compute(
                    new BigDecimal("100"),
                    new BigDecimal("1500"),
                    new BigDecimal("0.5"),
                    new BigDecimal("10")))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void missingTermsIsInvalid() {
    assertThatThrownBy(
            () -> calculator.compute(new BigDecimal("100"), new BigDecimal("1500"), null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
