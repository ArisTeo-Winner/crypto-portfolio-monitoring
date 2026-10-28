package com.mx.cryptomonitor.unit.transaction.domain.spinoff;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.mx.cryptomonitor.transaction.domain.spinoff.SpinoffEffect;
import com.mx.cryptomonitor.transaction.domain.spinoff.SpinoffEffectCalculator;

class SpinoffEffectCalculatorTest {

  private final SpinoffEffectCalculator calculator = new SpinoffEffectCalculator();

  @Test
  void splitsBasisByAllocationAndDistributesSpincoShares() {
    // 100 matriz, base 1000, ratio 0.5, 20% a la spinco.
    SpinoffEffect effect =
        calculator.compute(
            new BigDecimal("100"),
            new BigDecimal("1000"),
            new BigDecimal("0.5"),
            new BigDecimal("0.20"));

    assertThat(effect.spincoQuantity()).isEqualByComparingTo("50"); // 100 * 0.5
    assertThat(effect.spincoBasis()).isEqualByComparingTo("200"); // 1000 * 0.20
    assertThat(effect.retainedParentBasis()).isEqualByComparingTo("800"); // 1000 - 200
  }

  @Test
  void basisInvariantHolds() {
    SpinoffEffect effect =
        calculator.compute(
            new BigDecimal("37"),
            new BigDecimal("1234.56"),
            new BigDecimal("0.3333"),
            new BigDecimal("0.15"));

    assertThat(effect.retainedParentBasis().add(effect.spincoBasis()))
        .isEqualByComparingTo("1234.56");
  }

  @Test
  void nullAllocationIsDeferred() {
    assertThatThrownBy(
            () -> calculator.compute(BigDecimal.TEN, new BigDecimal("1000"), BigDecimal.ONE, null))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void allocationOutOfRangeIsInvalid() {
    assertThatThrownBy(
            () ->
                calculator.compute(
                    BigDecimal.TEN, new BigDecimal("1000"), BigDecimal.ONE, new BigDecimal("1.5")))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
