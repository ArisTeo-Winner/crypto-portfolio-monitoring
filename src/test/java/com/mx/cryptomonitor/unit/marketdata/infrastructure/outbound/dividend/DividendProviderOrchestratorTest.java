package com.mx.cryptomonitor.unit.marketdata.infrastructure.outbound.dividend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mx.cryptomonitor.marketdata.application.port.out.DividendEventData;
import com.mx.cryptomonitor.marketdata.infrastructure.outbound.databursatil.DataBursatilDividendAdapter;
import com.mx.cryptomonitor.marketdata.infrastructure.outbound.dividend.DividendProviderOrchestrator;
import com.mx.cryptomonitor.marketdata.infrastructure.outbound.eodhd.EodhdDividendAdapter;
import com.mx.cryptomonitor.marketdata.infrastructure.outbound.massive.MassiveDividendAdapter;

@ExtendWith(MockitoExtension.class)
class DividendProviderOrchestratorTest {

  @Mock private MassiveDividendAdapter massive;
  @Mock private DataBursatilDividendAdapter dataBursatil;
  @Mock private EodhdDividendAdapter eodhd;

  private DividendProviderOrchestrator orchestrator() {
    return new DividendProviderOrchestrator(massive, dataBursatil, eodhd);
  }

  private DividendEventData event(String ticker, String currency) {
    return new DividendEventData(
        ticker,
        LocalDate.of(2024, 6, 10),
        null,
        LocalDate.of(2024, 6, 17),
        null,
        new BigDecimal("0.20"),
        currency,
        (short) 4,
        "recurring",
        new BigDecimal("0.20"),
        null,
        "id:" + ticker);
  }

  @Test
  void usesMassiveWhenItReturnsData_andSkipsRest() {
    when(massive.fetchDividends("GOOGL")).thenReturn(List.of(event("GOOGL", "USD")));

    List<DividendEventData> result = orchestrator().fetchDividends("GOOGL");

    assertThat(result).hasSize(1);
    verify(dataBursatil, never()).fetchDividends("GOOGL");
    verify(eodhd, never()).fetchDividends("GOOGL");
  }

  @Test
  void routesMxnBmvSymbolToDataBursatil() {
    when(massive.fetchDividends("BIMBOA")).thenReturn(List.of());
    when(dataBursatil.fetchDividends("BIMBOA")).thenReturn(List.of(event("BIMBOA", "MXN")));

    List<DividendEventData> result = orchestrator().fetchDividends("BIMBOA");

    assertThat(result).hasSize(1);
    assertThat(result.get(0).currency()).isEqualTo("MXN");
    verify(eodhd, never()).fetchDividends("BIMBOA");
  }

  @Test
  void fallsBackToEodhdAsLastResortWhenOthersEmpty() {
    when(massive.fetchDividends("XYZ")).thenReturn(List.of());
    when(dataBursatil.fetchDividends("XYZ")).thenReturn(List.of());
    when(eodhd.fetchDividends("XYZ")).thenReturn(List.of(event("XYZ", "USD")));

    assertThat(orchestrator().fetchDividends("XYZ")).hasSize(1);
  }

  @Test
  void returnsEmptyWhenAllEmpty() {
    when(massive.fetchDividends("ZZZZ")).thenReturn(List.of());
    when(dataBursatil.fetchDividends("ZZZZ")).thenReturn(List.of());
    when(eodhd.fetchDividends("ZZZZ")).thenReturn(List.of());

    assertThat(orchestrator().fetchDividends("ZZZZ")).isEmpty();
  }
}
