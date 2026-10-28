package com.mx.cryptomonitor.unit.portfolio.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mx.cryptomonitor.marketdata.application.port.out.FxRatePort;
import com.mx.cryptomonitor.marketdata.application.port.out.UsdMxnRateDetail;
import com.mx.cryptomonitor.portfolio.application.dto.response.PortfolioEntryResponse;
import com.mx.cryptomonitor.portfolio.application.mapper.PortfolioEntryMapper;
import com.mx.cryptomonitor.portfolio.application.service.MoneyPresentationConverter;
import com.mx.cryptomonitor.portfolio.application.service.PortfolioEntryPresentationService;
import com.mx.cryptomonitor.portfolio.domain.model.PortfolioEntry;
import com.mx.cryptomonitor.portfolio.domain.model.PresentationCurrency;

class PortfolioEntryPresentationServiceTest {

  private final FxRatePort fxRatePort = mock(FxRatePort.class);
  private final PortfolioEntryPresentationService service =
      new PortfolioEntryPresentationService(
          new PortfolioEntryMapper(), new MoneyPresentationConverter(fxRatePort));

  private PortfolioEntry sampleEntry() {
    return PortfolioEntry.builder()
        .portfolioEntryId(UUID.randomUUID())
        .userId(UUID.randomUUID())
        .assetSymbol("MELI")
        .assetType("STOCK")
        .totalQuantity(new BigDecimal("10"))
        .totalInvested(new BigDecimal("80.00"))
        .averagePricePerUnit(new BigDecimal("8.00"))
        .lastTransactionPrice(new BigDecimal("10.00"))
        .currentValue(new BigDecimal("100.00"))
        .totalProfitLoss(new BigDecimal("20.00"))
        .build();
  }

  @Test
  void usdPresentationLeavesMoneyUntouched() {
    PortfolioEntryResponse response = service.present(sampleEntry(), PresentationCurrency.USD);

    assertThat(response.currentValue()).isEqualByComparingTo("100.00");
    assertThat(response.totalInvested()).isEqualByComparingTo("80.00");
    assertThat(response.totalProfitLoss()).isEqualByComparingTo("20.00");
    assertThat(response.totalQuantity()).isEqualByComparingTo("10");
    assertThat(response.presentation().displayCurrency()).isEqualTo("USD");
    assertThat(response.presentation().fxRate()).isEqualByComparingTo(BigDecimal.ONE);
  }

  @Test
  void mxnPresentationConvertsMoneyButNotQuantity() {
    when(fxRatePort.usdMxnRateDetail())
        .thenReturn(
            Optional.of(
                new UsdMxnRateDetail(
                    new BigDecimal("17.5147"),
                    "BANXICO",
                    OffsetDateTime.of(2026, 9, 26, 0, 0, 0, 0, ZoneOffset.UTC))));

    List<PortfolioEntryResponse> responses =
        service.present(List.of(sampleEntry()), PresentationCurrency.MXN);

    PortfolioEntryResponse response = responses.get(0);
    assertThat(response.currentValue()).isEqualByComparingTo("1751.47"); // 100 * 17.5147
    assertThat(response.totalInvested()).isEqualByComparingTo("1401.18"); // 80 * 17.5147
    assertThat(response.totalProfitLoss()).isEqualByComparingTo("350.29"); // 20 * 17.5147
    assertThat(response.totalQuantity()).isEqualByComparingTo("10"); // cantidad intacta
    assertThat(response.presentation().baseCurrency()).isEqualTo("USD");
    assertThat(response.presentation().displayCurrency()).isEqualTo("MXN");
    assertThat(response.presentation().fxRate()).isEqualByComparingTo("17.5147");
  }
}
