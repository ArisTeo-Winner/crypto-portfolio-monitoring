package com.mx.cryptomonitor.unit.tax.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.mx.cryptomonitor.tax.domain.model.AnnualRegimeTax;
import com.mx.cryptomonitor.tax.domain.model.TaxRegime;
import com.mx.cryptomonitor.tax.domain.model.TaxableSale;
import com.mx.cryptomonitor.tax.domain.service.EquityTaxAggregator;

class EquityTaxAggregatorTest {

  private final EquityTaxAggregator aggregator = new EquityTaxAggregator();

  @Test
  void netsGainsAndLossesWithinRegimeAndAppliesTenPercentCedular() {
    List<TaxableSale> sales =
        List.of(
            new TaxableSale(TaxRegime.MX_BOLSA, "MELI", 2026, new BigDecimal("5000.00")),
            new TaxableSale(TaxRegime.MX_BOLSA, "WALMEX", 2026, new BigDecimal("-2000.00")));

    AnnualRegimeTax result = only(aggregator.aggregate(sales, 2026));

    assertThat(result.regime()).isEqualTo(TaxRegime.MX_BOLSA);
    assertThat(result.grossGain()).isEqualByComparingTo("5000.00");
    assertThat(result.grossLoss()).isEqualByComparingTo("2000.00");
    assertThat(result.netResult()).isEqualByComparingTo("3000.00");
    assertThat(result.taxableBase()).isEqualByComparingTo("3000.00");
    assertThat(result.lossCarryforward()).isEqualByComparingTo("0.00");
    assertThat(result.cedularRate()).isEqualByComparingTo("0.10");
    assertThat(result.estimatedTax()).isEqualByComparingTo("300.00");
  }

  @Test
  void netLossProducesCarryforwardAndZeroTax() {
    List<TaxableSale> sales =
        List.of(
            new TaxableSale(TaxRegime.MX_BOLSA, "MELI", 2026, new BigDecimal("1000.00")),
            new TaxableSale(TaxRegime.MX_BOLSA, "WALMEX", 2026, new BigDecimal("-4500.00")));

    AnnualRegimeTax result = only(aggregator.aggregate(sales, 2026));

    assertThat(result.netResult()).isEqualByComparingTo("-3500.00");
    assertThat(result.taxableBase()).isEqualByComparingTo("0.00");
    assertThat(result.lossCarryforward()).isEqualByComparingTo("3500.00");
    assertThat(result.estimatedTax()).isEqualByComparingTo("0.00");
  }

  @Test
  void usEquityNetGainCarriesNoCedularRateInThisPhase() {
    List<TaxableSale> sales =
        List.of(new TaxableSale(TaxRegime.US_EQUITY, "AAPL", 2026, new BigDecimal("1200.00")));

    AnnualRegimeTax result = only(aggregator.aggregate(sales, 2026));

    assertThat(result.regime()).isEqualTo(TaxRegime.US_EQUITY);
    assertThat(result.taxableBase()).isEqualByComparingTo("1200.00");
    assertThat(result.cedularRate()).isNull();
    assertThat(result.estimatedTax()).isNull();
  }

  @Test
  void onlySalesOfTheRequestedFiscalYearAreAggregated() {
    List<TaxableSale> sales =
        List.of(
            new TaxableSale(TaxRegime.MX_BOLSA, "MELI", 2025, new BigDecimal("9999.00")),
            new TaxableSale(TaxRegime.MX_BOLSA, "MELI", 2026, new BigDecimal("1000.00")));

    AnnualRegimeTax result = only(aggregator.aggregate(sales, 2026));

    assertThat(result.grossGain()).isEqualByComparingTo("1000.00");
    assertThat(result.estimatedTax()).isEqualByComparingTo("100.00");
  }

  @Test
  void separatesResultsPerRegime() {
    List<TaxableSale> sales =
        List.of(
            new TaxableSale(TaxRegime.MX_BOLSA, "MELI", 2026, new BigDecimal("3000.00")),
            new TaxableSale(TaxRegime.US_EQUITY, "AAPL", 2026, new BigDecimal("500.00")));

    List<AnnualRegimeTax> results = aggregator.aggregate(sales, 2026);

    assertThat(results).hasSize(2);
    assertThat(results)
        .extracting(AnnualRegimeTax::regime)
        .containsExactlyInAnyOrder(TaxRegime.MX_BOLSA, TaxRegime.US_EQUITY);
  }

  private AnnualRegimeTax only(List<AnnualRegimeTax> results) {
    assertThat(results).hasSize(1);
    return results.get(0);
  }
}
