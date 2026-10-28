package com.mx.cryptomonitor.unit.portfolio.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.mx.cryptomonitor.marketdata.application.port.out.FxRatePort;
import com.mx.cryptomonitor.marketdata.application.port.out.UsdMxnRateDetail;
import com.mx.cryptomonitor.portfolio.application.dto.response.PortfolioHistoryMeta;
import com.mx.cryptomonitor.portfolio.application.dto.response.PortfolioHistoryPointResponse;
import com.mx.cryptomonitor.portfolio.application.dto.response.PortfolioHistoryResponse;
import com.mx.cryptomonitor.portfolio.application.dto.response.PortfolioHoldingsPerformanceResponse;
import com.mx.cryptomonitor.portfolio.application.dto.response.PortfolioHoldingsPerformanceResponse.SeriesPoint;
import com.mx.cryptomonitor.portfolio.application.dto.response.ReturnMetricsResponse;
import com.mx.cryptomonitor.portfolio.application.service.MoneyPresentationConverter;
import com.mx.cryptomonitor.portfolio.application.service.PortfolioPerformancePresentationService;
import com.mx.cryptomonitor.portfolio.domain.model.PresentationCurrency;

class PortfolioPerformancePresentationServiceTest {

  private final FxRatePort fxRatePort = mock(FxRatePort.class);
  private final PortfolioPerformancePresentationService service =
      new PortfolioPerformancePresentationService(new MoneyPresentationConverter(fxRatePort));

  private void stubMxnRate() {
    when(fxRatePort.usdMxnRateDetail())
        .thenReturn(
            Optional.of(
                new UsdMxnRateDetail(
                    new BigDecimal("17.5147"),
                    "BANXICO",
                    OffsetDateTime.of(2026, 9, 26, 0, 0, 0, 0, ZoneOffset.UTC))));
  }

  @Test
  void holdingsPerformanceConvertsMoneyKeepsPercent() {
    stubMxnRate();
    PortfolioHoldingsPerformanceResponse usd =
        new PortfolioHoldingsPerformanceResponse(
            List.of(new SeriesPoint(1000L, new BigDecimal("100.00"))),
            true,
            new BigDecimal("20.00"),
            new BigDecimal("25.00"),
            new BigDecimal("80.00"),
            LocalDate.of(2026, 5, 15));

    PortfolioHoldingsPerformanceResponse mxn = service.present(usd, PresentationCurrency.MXN);

    assertThat(mxn.series().get(0).value()).isEqualByComparingTo("1751.47"); // 100 * 17.5147
    assertThat(mxn.allTimeProfit()).isEqualByComparingTo("350.29"); // 20 * 17.5147
    assertThat(mxn.costBasis()).isEqualByComparingTo("1401.18"); // 80 * 17.5147
    assertThat(mxn.allTimeProfitPercent()).isEqualByComparingTo("25.00"); // invariante
    assertThat(mxn.presentation().displayCurrency()).isEqualTo("MXN");
    assertThat(mxn.presentation().rateProvider()).isEqualTo("BANXICO");
  }

  @Test
  void historyConvertsSeriesAndReturnsMoneyKeepsTwrMwr() {
    stubMxnRate();
    PortfolioHistoryMeta meta =
        new PortfolioHistoryMeta(
            "1M",
            "DAILY",
            1000L,
            2000L,
            "USD",
            1,
            new ReturnMetricsResponse(
                new BigDecimal("0.10"),
                new BigDecimal("0.12"),
                new BigDecimal("20.00"),
                new BigDecimal("80.00")),
            false,
            List.of());
    PortfolioHistoryResponse usd =
        new PortfolioHistoryResponse(
            meta, List.of(new PortfolioHistoryPointResponse(1000L, new BigDecimal("100.00"))));

    PortfolioHistoryResponse mxn = service.present(usd, PresentationCurrency.MXN);

    assertThat(mxn.series().get(0).value()).isEqualByComparingTo("1751.47");
    assertThat(mxn.meta().currency()).isEqualTo("MXN");
    assertThat(mxn.meta().returns().absoluteGain()).isEqualByComparingTo("350.29");
    assertThat(mxn.meta().returns().totalInvested()).isEqualByComparingTo("1401.18");
    assertThat(mxn.meta().returns().twr()).isEqualByComparingTo("0.10"); // invariante
    assertThat(mxn.meta().returns().mwr()).isEqualByComparingTo("0.12"); // invariante
    assertThat(mxn.meta().presentation().displayCurrency()).isEqualTo("MXN");
  }

  @Test
  void usdLeavesEverythingUntouched() {
    PortfolioHistoryMeta meta =
        new PortfolioHistoryMeta("1M", "DAILY", 1000L, 2000L, "USD", 1, null, false, List.of());
    PortfolioHistoryResponse usd =
        new PortfolioHistoryResponse(
            meta, List.of(new PortfolioHistoryPointResponse(1000L, new BigDecimal("100.00"))));

    PortfolioHistoryResponse result = service.present(usd, PresentationCurrency.USD);

    assertThat(result.series().get(0).value()).isEqualByComparingTo("100.00");
    assertThat(result.meta().currency()).isEqualTo("USD");
    assertThat(result.meta().presentation().displayCurrency()).isEqualTo("USD");
  }
}
