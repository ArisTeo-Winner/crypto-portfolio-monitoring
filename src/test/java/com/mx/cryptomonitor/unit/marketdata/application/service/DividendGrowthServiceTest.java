package com.mx.cryptomonitor.unit.marketdata.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mx.cryptomonitor.marketdata.application.dto.response.DividendGrowthResponse;
import com.mx.cryptomonitor.marketdata.application.service.DividendGrowthService;
import com.mx.cryptomonitor.marketdata.domain.repository.DividendActionDetailRepository;

@ExtendWith(MockitoExtension.class)
class DividendGrowthServiceTest {

  @Mock private DividendActionDetailRepository dividendDetailRepository;

  /** Fija el anio en curso para una CAGR determinista. */
  private DividendGrowthService serviceAtYear(int year) {
    return new DividendGrowthService(dividendDetailRepository) {
      @Override
      protected int currentYear() {
        return year;
      }
    };
  }

  private Object[] row(int year, String dps) {
    return new Object[] {LocalDate.of(year, 6, 10), new BigDecimal(dps), "USD"};
  }

  @Test
  void mapsRecurringDpsAndComputesCagr() {
    when(dividendDetailRepository.findRecurringDpsBySymbol("KO"))
        .thenReturn(List.of(row(2022, "2.00"), row(2023, "2.20"), row(2024, "0.60")));

    DividendGrowthResponse response = serviceAtYear(2024).growthFor("ko");

    assertThat(response.symbol()).isEqualTo("KO");
    assertThat(response.annualDividends())
        .extracting(DividendGrowthResponse.AnnualDividendPoint::year)
        .containsExactly(2022, 2023); // 2024 excluido
    assertThat(response.cagr1y()).isEqualByComparingTo("0.100000");
    assertThat(response.cagr3y()).isNull();
    assertThat(response.currency()).isEqualTo("USD");
  }

  @Test
  void blankSymbolYieldsEmptyResponse() {
    DividendGrowthResponse response = serviceAtYear(2024).growthFor("  ");

    assertThat(response.annualDividends()).isEmpty();
    assertThat(response.cagr1y()).isNull();
  }

  @Test
  void unknownSymbolWithNoDividendsYieldsEmpty() {
    when(dividendDetailRepository.findRecurringDpsBySymbol("NODIV")).thenReturn(List.of());

    DividendGrowthResponse response = serviceAtYear(2024).growthFor("NODIV");

    assertThat(response.symbol()).isEqualTo("NODIV");
    assertThat(response.annualDividends()).isEmpty();
  }
}
