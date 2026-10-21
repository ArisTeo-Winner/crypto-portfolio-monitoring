package com.mx.cryptomonitor.unit.marketdata.domain.dividend;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.mx.cryptomonitor.marketdata.domain.dividend.DividendGrowthCalculator;
import com.mx.cryptomonitor.marketdata.domain.dividend.DividendGrowthCalculator.DatedDps;
import com.mx.cryptomonitor.marketdata.domain.dividend.DividendGrowthResult;

class DividendGrowthCalculatorTest {

  private final DividendGrowthCalculator calculator = new DividendGrowthCalculator();

  private DatedDps q(int year, int month, String dps) {
    return new DatedDps(LocalDate.of(year, month, 10), new BigDecimal(dps));
  }

  @Test
  void aggregatesQuarterlyIntoAnnualTotalsExcludesCurrentYearAndComputesCagr() {
    List<DatedDps> dividends =
        List.of(
            // 2020 total = 2.00
            q(2020, 3, "0.50"),
            q(2020, 6, "0.50"),
            q(2020, 9, "0.50"),
            q(2020, 12, "0.50"),
            // 2022 total = 2.00
            q(2022, 3, "0.50"),
            q(2022, 6, "0.50"),
            q(2022, 9, "0.50"),
            q(2022, 12, "0.50"),
            // 2023 total = 2.20
            q(2023, 3, "0.55"),
            q(2023, 6, "0.55"),
            q(2023, 9, "0.55"),
            q(2023, 12, "0.55"),
            // 2024 en curso -> debe excluirse
            q(2024, 3, "0.60"));

    DividendGrowthResult result = calculator.compute("KO", dividends, 2024);

    assertThat(result.annualDividends())
        .extracting(DividendGrowthResult.AnnualDividend::year)
        .containsExactly(2020, 2022, 2023); // sin 2024
    assertThat(result.annualDividends().get(2).totalDps()).isEqualByComparingTo("2.20");

    assertThat(result.cagr1y()).isEqualByComparingTo("0.100000"); // 2.20/2.00 - 1
    assertThat(result.cagr3y()).isEqualByComparingTo("0.032280"); // (2.20/2.00)^(1/3) - 1
    assertThat(result.cagr5y()).isNull(); // falta 2018
    assertThat(result.cagr10y()).isNull();
  }

  @Test
  void fiveYearDoublingGivesExpectedCagr() {
    List<DatedDps> dividends = List.of(q(2018, 6, "1.00"), q(2023, 6, "2.00")); // x2 en 5 anios

    DividendGrowthResult result = calculator.compute("XYZ", dividends, 2024);

    assertThat(result.cagr5y()).isEqualByComparingTo("0.148698"); // 2^(1/5) - 1
  }

  @Test
  void emptyInputYieldsEmptyResult() {
    DividendGrowthResult result = calculator.compute("KO", List.of(), 2024);

    assertThat(result.annualDividends()).isEmpty();
    assertThat(result.cagr1y()).isNull();
  }

  @Test
  void onlyCurrentYearYieldsEmptyResult() {
    DividendGrowthResult result =
        calculator.compute("KO", List.of(q(2024, 3, "0.60"), q(2024, 6, "0.60")), 2024);

    assertThat(result.annualDividends()).isEmpty();
  }

  @Test
  void cagrNullWhenStartYearMissing() {
    // Solo 2022 y 2023: 1y existe, 3y/5y/10y no.
    DividendGrowthResult result =
        calculator.compute("KO", List.of(q(2022, 6, "2.00"), q(2023, 6, "2.20")), 2024);

    assertThat(result.cagr1y()).isEqualByComparingTo("0.100000");
    assertThat(result.cagr3y()).isNull();
  }
}
