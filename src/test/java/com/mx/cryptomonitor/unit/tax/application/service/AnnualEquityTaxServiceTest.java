package com.mx.cryptomonitor.unit.tax.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mx.cryptomonitor.portfolio.application.port.in.RealizedGainsQueryPort;
import com.mx.cryptomonitor.portfolio.application.port.in.RealizedGainsQueryPort.CostingMethod;
import com.mx.cryptomonitor.portfolio.application.port.in.RealizedGainsQueryPort.RealizedGain;
import com.mx.cryptomonitor.tax.application.port.in.CalculateAnnualEquityTaxUseCase.AnnualEquityTaxReport;
import com.mx.cryptomonitor.tax.application.port.in.CalculateAnnualEquityTaxUseCase.RegimeTaxLine;
import com.mx.cryptomonitor.tax.application.service.AnnualEquityTaxService;

@ExtendWith(MockitoExtension.class)
class AnnualEquityTaxServiceTest {

  private static final UUID USER = UUID.randomUUID();

  @Mock private RealizedGainsQueryPort realizedGainsQueryPort;

  @InjectMocks private AnnualEquityTaxService service;

  @Test
  void routesMxnSalesToAverageCostBolsaAndUsdSalesToFifoUsEquity() {
    RealizedGain meliAcb =
        new RealizedGain("MELI", "STOCK", "MXN", LocalDate.of(2026, 3, 10), new BigDecimal("4000"));
    RealizedGain aaplAcb =
        new RealizedGain("AAPL", "STOCK", "USD", LocalDate.of(2026, 5, 1), new BigDecimal("999"));
    RealizedGain aaplFifo =
        new RealizedGain("AAPL", "STOCK", "USD", LocalDate.of(2026, 5, 1), new BigDecimal("1500"));
    RealizedGain meliFifo =
        new RealizedGain("MELI", "STOCK", "MXN", LocalDate.of(2026, 3, 10), new BigDecimal("111"));

    when(realizedGainsQueryPort.realizedGains(USER, CostingMethod.AVERAGE_COST))
        .thenReturn(List.of(meliAcb, aaplAcb));
    when(realizedGainsQueryPort.realizedGains(USER, CostingMethod.FIFO))
        .thenReturn(List.of(aaplFifo, meliFifo));

    AnnualEquityTaxReport report = service.calculate(USER, 2026);

    assertThat(report.fiscalYear()).isEqualTo(2026);
    assertThat(report.disclaimer()).containsIgnoringCase("no asesoría fiscal");

    RegimeTaxLine bolsa = line(report, "MX_BOLSA");
    assertThat(bolsa.grossGain()).isEqualByComparingTo("4000.00");
    assertThat(bolsa.estimatedTax()).isEqualByComparingTo("400.00");

    RegimeTaxLine us = line(report, "US_EQUITY");
    assertThat(us.grossGain()).isEqualByComparingTo("1500.00");
    assertThat(us.cedularRate()).isNull();
  }

  @Test
  void emptyHistoryProducesReportWithNoRegimeLines() {
    when(realizedGainsQueryPort.realizedGains(USER, CostingMethod.AVERAGE_COST))
        .thenReturn(List.of());
    when(realizedGainsQueryPort.realizedGains(USER, CostingMethod.FIFO)).thenReturn(List.of());

    AnnualEquityTaxReport report = service.calculate(USER, 2026);

    assertThat(report.regimes()).isEmpty();
  }

  private RegimeTaxLine line(AnnualEquityTaxReport report, String regime) {
    return report.regimes().stream()
        .filter(line -> line.regime().equals(regime))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Falta el régimen " + regime));
  }
}
