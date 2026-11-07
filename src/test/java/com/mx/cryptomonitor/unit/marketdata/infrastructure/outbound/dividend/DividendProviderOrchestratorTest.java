package com.mx.cryptomonitor.unit.marketdata.infrastructure.outbound.dividend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mx.cryptomonitor.asset.application.port.in.AssetCatalogQueryPort;
import com.mx.cryptomonitor.marketdata.application.port.out.DividendEventData;
import com.mx.cryptomonitor.marketdata.application.port.out.DividendProviderUnavailableException;
import com.mx.cryptomonitor.marketdata.infrastructure.outbound.alphavantage.AlphaVantageDividendAdapter;
import com.mx.cryptomonitor.marketdata.infrastructure.outbound.databursatil.DataBursatilDividendAdapter;
import com.mx.cryptomonitor.marketdata.infrastructure.outbound.dividend.DividendProviderOrchestrator;
import com.mx.cryptomonitor.marketdata.infrastructure.outbound.eodhd.EodhdDividendAdapter;
import com.mx.cryptomonitor.marketdata.infrastructure.outbound.fmp.FmpDividendAdapter;
import com.mx.cryptomonitor.marketdata.infrastructure.outbound.massive.MassiveDividendAdapter;

@ExtendWith(MockitoExtension.class)
class DividendProviderOrchestratorTest {

  @Mock private MassiveDividendAdapter massive;
  @Mock private FmpDividendAdapter fmp;
  @Mock private AlphaVantageDividendAdapter alphaVantage;
  @Mock private DataBursatilDividendAdapter dataBursatil;
  @Mock private EodhdDividendAdapter eodhd;
  @Mock private AssetCatalogQueryPort assetCatalog;

  private DividendProviderOrchestrator orchestrator() {
    return new DividendProviderOrchestrator(
        massive, fmp, alphaVantage, dataBursatil, eodhd, assetCatalog);
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
    verify(fmp, never()).fetchDividends("GOOGL");
    verify(alphaVantage, never()).fetchDividends("GOOGL");
    verify(dataBursatil, never()).fetchDividends("GOOGL");
    verify(eodhd, never()).fetchDividends("GOOGL");
  }

  @Test
  void fmpFillsGoogGapBeforeAlphaVantage_andWithoutBmvCredits() {
    when(massive.fetchDividends("GOOG")).thenReturn(List.of());
    when(fmp.fetchDividends("GOOG")).thenReturn(List.of(event("GOOG", "USD")));

    List<DividendEventData> result = orchestrator().fetchDividends("GOOG");

    assertThat(result).hasSize(1);
    assertThat(result.get(0).ticker()).isEqualTo("GOOG");
    verify(alphaVantage, never()).fetchDividends("GOOG");
    verify(dataBursatil, never()).fetchDividends("GOOG");
    verify(eodhd, never()).fetchDividends("GOOG");
  }

  @Test
  void routesMxnBmvSymbolToDataBursatilAfterUsProvidersEmpty() {
    when(massive.fetchDividends("BIMBOA")).thenReturn(List.of());
    when(fmp.fetchDividends("BIMBOA")).thenReturn(List.of());
    when(alphaVantage.fetchDividends("BIMBOA")).thenReturn(List.of());
    when(dataBursatil.fetchDividends("BIMBOA")).thenReturn(List.of(event("BIMBOA", "MXN")));

    List<DividendEventData> result = orchestrator().fetchDividends("BIMBOA");

    assertThat(result).hasSize(1);
    assertThat(result.get(0).currency()).isEqualTo("MXN");
    verify(eodhd, never()).fetchDividends("BIMBOA");
  }

  /** ADR-0025 F3: con moneda MXN del catálogo, DataBursatil va PRIMERO (no gasta cuota US). */
  @Test
  void routesMxnSymbolToDataBursatilFirstWhenCatalogSaysMxn() {
    when(assetCatalog.findCurrencyBySymbol("BIMBOA")).thenReturn(Optional.of("MXN"));
    when(dataBursatil.fetchDividends("BIMBOA")).thenReturn(List.of(event("BIMBOA", "MXN")));

    List<DividendEventData> result = orchestrator().fetchDividends("BIMBOA");

    assertThat(result).hasSize(1);
    assertThat(result.get(0).currency()).isEqualTo("MXN");
    verify(massive, never()).fetchDividends("BIMBOA");
    verify(fmp, never()).fetchDividends("BIMBOA");
    verify(alphaVantage, never()).fetchDividends("BIMBOA");
    verify(eodhd, never()).fetchDividends("BIMBOA");
  }

  @Test
  void fallsBackToEodhdAsLastResortWhenOthersEmpty() {
    when(massive.fetchDividends("XYZ")).thenReturn(List.of());
    when(fmp.fetchDividends("XYZ")).thenReturn(List.of());
    when(alphaVantage.fetchDividends("XYZ")).thenReturn(List.of());
    when(dataBursatil.fetchDividends("XYZ")).thenReturn(List.of());
    when(eodhd.fetchDividends("XYZ")).thenReturn(List.of(event("XYZ", "USD")));

    assertThat(orchestrator().fetchDividends("XYZ")).hasSize(1);
  }

  @Test
  void returnsEmptyWhenAllEmpty() {
    when(massive.fetchDividends("ZZZZ")).thenReturn(List.of());
    when(fmp.fetchDividends("ZZZZ")).thenReturn(List.of());
    when(alphaVantage.fetchDividends("ZZZZ")).thenReturn(List.of());
    when(dataBursatil.fetchDividends("ZZZZ")).thenReturn(List.of());
    when(eodhd.fetchDividends("ZZZZ")).thenReturn(List.of());

    assertThat(orchestrator().fetchDividends("ZZZZ")).isEmpty();
  }

  /**
   * ADR-0025 F2: si nadie trae datos pero al menos un proveedor falló, el vacío NO es confiable.
   */
  @Test
  void throwsWhenNoDataAndAtLeastOneProviderFailed() {
    when(massive.fetchDividends("ERR"))
        .thenThrow(new DividendProviderUnavailableException("Massive down"));
    when(fmp.fetchDividends("ERR")).thenReturn(List.of());
    when(alphaVantage.fetchDividends("ERR")).thenReturn(List.of());
    when(dataBursatil.fetchDividends("ERR")).thenReturn(List.of());
    when(eodhd.fetchDividends("ERR")).thenReturn(List.of());

    assertThatThrownBy(() -> orchestrator().fetchDividends("ERR"))
        .isInstanceOf(DividendProviderUnavailableException.class);
  }

  /** Un fallo de un proveedor NO impide usar los datos de otro que sí respondió. */
  @Test
  void recoversWithDataFromAnotherProviderDespiteOneFailure() {
    when(massive.fetchDividends("GOOG"))
        .thenThrow(new DividendProviderUnavailableException("Massive down"));
    when(fmp.fetchDividends("GOOG")).thenReturn(List.of(event("GOOG", "USD")));

    List<DividendEventData> result = orchestrator().fetchDividends("GOOG");

    assertThat(result).hasSize(1);
    verify(eodhd, never()).fetchDividends("GOOG");
  }
}
