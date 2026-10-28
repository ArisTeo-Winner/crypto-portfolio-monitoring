package com.mx.cryptomonitor.unit.transaction.domain.dividend;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.mx.cryptomonitor.transaction.domain.dividend.DividendTaxBreakdown;
import com.mx.cryptomonitor.transaction.domain.dividend.DividendTaxCalculator;
import com.mx.cryptomonitor.transaction.domain.dividend.DividendTaxRates;

class DividendTaxCalculatorTest {

  private final DividendTaxCalculator calculator = new DividendTaxCalculator();

  @Test
  void googlSingleShareAppliesChainedUsThenMxWithholding() {
    DividendTaxBreakdown breakdown =
        calculator.foreignCashDividend(BigDecimal.ONE, new BigDecimal("0.20"));

    assertThat(breakdown.grossAmount()).isEqualByComparingTo("0.20");
    assertThat(breakdown.sourceWithheld()).isEqualByComparingTo("0.02"); // US 10% sobre bruto
    assertThat(breakdown.localWithheld()).isEqualByComparingTo("0.018"); // MX 10% sobre 0.18 neto
    assertThat(breakdown.totalWithheld()).isEqualByComparingTo("0.038");
    assertThat(breakdown.netReceived()).isEqualByComparingTo("0.162");
  }

  @Test
  void localWithholdingIsOnNetAfterSourceNotOnGross() {
    // Si MX se aplicara sobre el bruto el neto seria 0.16; la cadena correcta da 0.162.
    DividendTaxBreakdown breakdown =
        calculator.foreignCashDividend(BigDecimal.ONE, new BigDecimal("0.20"));

    assertThat(breakdown.netReceived()).isNotEqualByComparingTo("0.16");
    assertThat(breakdown.netReceived()).isEqualByComparingTo("0.162");
  }

  @Test
  void netPlusWithholdingsEqualsGrossExactly() {
    DividendTaxBreakdown breakdown =
        calculator.foreignCashDividend(new BigDecimal("13.37"), new BigDecimal("0.83"));

    assertThat(
            breakdown.sourceWithheld().add(breakdown.localWithheld()).add(breakdown.netReceived()))
        .isEqualByComparingTo(breakdown.grossAmount());
  }

  @Test
  void scalesWithQuantity() {
    DividendTaxBreakdown breakdown =
        calculator.foreignCashDividend(new BigDecimal("100"), new BigDecimal("0.20"));

    assertThat(breakdown.grossAmount()).isEqualByComparingTo("20");
    assertThat(breakdown.sourceWithheld()).isEqualByComparingTo("2");
    assertThat(breakdown.localWithheld()).isEqualByComparingTo("1.8");
    assertThat(breakdown.netReceived()).isEqualByComparingTo("16.2");
  }

  @Test
  void honorsCustomRates() {
    DividendTaxCalculator custom =
        new DividendTaxCalculator(
            new DividendTaxRates(new BigDecimal("0.30"), new BigDecimal("0.10")));

    DividendTaxBreakdown breakdown =
        custom.foreignCashDividend(BigDecimal.ONE, new BigDecimal("1.00"));

    assertThat(breakdown.sourceWithheld()).isEqualByComparingTo("0.30");
    assertThat(breakdown.localWithheld()).isEqualByComparingTo("0.07"); // 10% de 0.70
    assertThat(breakdown.netReceived()).isEqualByComparingTo("0.63");
  }

  @Test
  void zeroQuantityYieldsZeroChain() {
    DividendTaxBreakdown breakdown =
        calculator.foreignCashDividend(BigDecimal.ZERO, new BigDecimal("0.20"));

    assertThat(breakdown.grossAmount()).isEqualByComparingTo("0");
    assertThat(breakdown.totalWithheld()).isEqualByComparingTo("0");
    assertThat(breakdown.netReceived()).isEqualByComparingTo("0");
  }

  @Test
  void nullInputsAreTreatedAsZero() {
    DividendTaxBreakdown breakdown = calculator.foreignCashDividend(null, null);

    assertThat(breakdown.grossAmount()).isEqualByComparingTo("0");
    assertThat(breakdown.netReceived()).isEqualByComparingTo("0");
  }
}
