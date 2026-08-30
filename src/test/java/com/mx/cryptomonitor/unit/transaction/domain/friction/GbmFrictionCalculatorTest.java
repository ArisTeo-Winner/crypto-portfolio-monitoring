package com.mx.cryptomonitor.unit.transaction.domain.friction;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.mx.cryptomonitor.transaction.domain.friction.FrictionBreakdown;
import com.mx.cryptomonitor.transaction.domain.friction.FrictionRates;
import com.mx.cryptomonitor.transaction.domain.friction.FrictionSide;
import com.mx.cryptomonitor.transaction.domain.friction.GbmFrictionCalculator;
import com.mx.cryptomonitor.transaction.domain.friction.ReviewStatus;

class GbmFrictionCalculatorTest {

  private final GbmFrictionCalculator calculator = new GbmFrictionCalculator();

  @Test
  void usesInjectedRatesInsteadOfHardcodedDefaults() {
    // Tolerancia amplia (10.00): un total reportado que con la tolerancia estandar (0.05) daria
    // REQUIERE_REVISION, aqui debe quedar OK -> prueba que la tasa inyectada se aplica.
    GbmFrictionCalculator custom =
        new GbmFrictionCalculator(
            new FrictionRates(
                new BigDecimal("0.0025"), new BigDecimal("0.16"), new BigDecimal("10.00")));

    FrictionBreakdown breakdown =
        custom.mexicanEquity(
            FrictionSide.BUY,
            new BigDecimal("100"),
            new BigDecimal("15.50"),
            new BigDecimal("1560.00"));

    assertThat(breakdown.reviewStatus()).isEqualTo(ReviewStatus.OK);
  }

  @Test
  void fmtyGoldenTicketMatchesGbmAppToTheCent() {
    FrictionBreakdown breakdown =
        calculator.mexicanEquity(
            FrictionSide.BUY,
            new BigDecimal("100"),
            new BigDecimal("15.50"),
            new BigDecimal("1554.49"));

    assertThat(breakdown.grossAmount()).isEqualByComparingTo("1550.00");
    assertThat(breakdown.brokerCommission()).isEqualByComparingTo("3.87");
    assertThat(breakdown.brokerIva()).isEqualByComparingTo("0.62");
    assertThat(breakdown.otherFees()).isEqualByComparingTo("0.00");
    assertThat(breakdown.totalFrictionCost()).isEqualByComparingTo("4.49");
    assertThat(breakdown.finalNetCost()).isEqualByComparingTo("1554.49");
    assertThat(breakdown.adjustedUnitPrice()).isEqualByComparingTo("15.5449");
    assertThat(breakdown.reviewStatus()).isEqualTo(ReviewStatus.OK);
  }

  @Test
  void ivaIsComputedOnRawCommissionNotOnTruncatedDisplayValue() {
    FrictionBreakdown breakdown =
        calculator.mexicanEquity(
            FrictionSide.BUY, new BigDecimal("100"), new BigDecimal("15.50"), null);

    // Comisión cruda = 3.875 -> se muestra 3.87 (truncada), pero el IVA = 3.875 * 0.16 = 0.62.
    // Sobre la comisión mostrada daría 3.87 * 0.16 = 0.6192 -> 0.62 no coincidiría con GBM.
    assertThat(breakdown.brokerCommission()).isEqualByComparingTo("3.87");
    assertThat(breakdown.brokerIva()).isEqualByComparingTo("0.62");
  }

  @Test
  void crclGoldenTicketMatchesDriveWealthConfirmation() {
    FrictionBreakdown breakdown =
        calculator.driveWealth(
            FrictionSide.BUY,
            new BigDecimal("0.95368698"),
            new BigDecimal("103.76"),
            new BigDecimal("0.25"),
            new BigDecimal("0.00"),
            new BigDecimal("0.00"),
            new BigDecimal("104.01"));

    assertThat(breakdown.grossAmount()).isEqualByComparingTo("103.76");
    assertThat(breakdown.brokerCommission()).isEqualByComparingTo("0.25");
    assertThat(breakdown.brokerIva()).isEqualByComparingTo("0.00");
    assertThat(breakdown.otherFees()).isEqualByComparingTo("0.00");
    assertThat(breakdown.totalFrictionCost()).isEqualByComparingTo("0.25");
    assertThat(breakdown.finalNetCost()).isEqualByComparingTo("104.01");
    assertThat(breakdown.reviewStatus()).isEqualTo(ReviewStatus.OK);
  }

  @Test
  void sellSubtractsFrictionFromGross() {
    FrictionBreakdown breakdown =
        calculator.mexicanEquity(
            FrictionSide.SELL, new BigDecimal("100"), new BigDecimal("15.50"), null);

    assertThat(breakdown.totalFrictionCost()).isEqualByComparingTo("4.49");
    assertThat(breakdown.finalNetCost()).isEqualByComparingTo("1545.51");
  }

  @Test
  void feeAccessorEqualsTotalFrictionCostForAcbEngine() {
    FrictionBreakdown breakdown =
        calculator.mexicanEquity(
            FrictionSide.BUY, new BigDecimal("100"), new BigDecimal("15.50"), null);

    assertThat(breakdown.fee()).isEqualByComparingTo(breakdown.totalFrictionCost());
  }

  @Test
  void discrepancyBeyondToleranceFlagsForReview() {
    FrictionBreakdown breakdown =
        calculator.mexicanEquity(
            FrictionSide.BUY,
            new BigDecimal("100"),
            new BigDecimal("15.50"),
            new BigDecimal("1554.60"));

    assertThat(breakdown.reviewStatus()).isEqualTo(ReviewStatus.REQUIERE_REVISION);
  }

  @Test
  void discrepancyWithinToleranceStaysOk() {
    FrictionBreakdown breakdown =
        calculator.mexicanEquity(
            FrictionSide.BUY,
            new BigDecimal("100"),
            new BigDecimal("15.50"),
            new BigDecimal("1554.53"));

    assertThat(breakdown.reviewStatus()).isEqualTo(ReviewStatus.OK);
  }

  @Test
  void nullReportedTotalIsNotFlagged() {
    FrictionBreakdown breakdown =
        calculator.mexicanEquity(
            FrictionSide.BUY, new BigDecimal("100"), new BigDecimal("15.50"), null);

    assertThat(breakdown.reviewStatus()).isEqualTo(ReviewStatus.OK);
  }
}
