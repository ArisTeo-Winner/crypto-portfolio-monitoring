package com.mx.cryptomonitor.unit.marketdata.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mx.cryptomonitor.marketdata.application.port.out.BmvMarketDataPort;
import com.mx.cryptomonitor.marketdata.application.port.out.MarketDataProvider;
import com.mx.cryptomonitor.marketdata.application.port.out.MxnSymbolLookupPort;
import com.mx.cryptomonitor.marketdata.application.service.HybridQuoteService;
import com.mx.cryptomonitor.marketdata.domain.model.BmvFxQuote;
import com.mx.cryptomonitor.marketdata.domain.model.BmvQuote;
import com.mx.cryptomonitor.marketdata.domain.model.HybridPriceQuote;
import com.mx.cryptomonitor.marketdata.domain.model.MarketFxSnapshotEntity;
import com.mx.cryptomonitor.marketdata.domain.repository.MarketFxSnapshotRepository;
import com.mx.cryptomonitor.marketdata.domain.repository.MarketPriceSnapshotRepository;

import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class HybridQuoteServiceTest {

  @Mock private BmvMarketDataPort databursatil;
  @Mock private MarketDataProvider stockQuoteOrchestrator;
  @Mock private MxnSymbolLookupPort mxnSymbolLookupPort;
  @Mock private MarketPriceSnapshotRepository snapshotRepository;
  @Mock private MarketFxSnapshotRepository fxSnapshotRepository;

  @InjectMocks private HybridQuoteService hybridQuoteService;

  // -------------------------------------------------------------------------
  // C4a — MXN usa DataBursatil, no el orquestador
  // -------------------------------------------------------------------------

  @Test
  void getCurrentPriceForMxnUsesDataBursatilAndAppendsAsteriskToSymbol() {
    BmvQuote quote =
        new BmvQuote(
            11.25,
            11.20,
            11.10,
            11.30,
            11.00,
            0.05,
            0.40,
            100000.0,
            11.10,
            1125000.0,
            "2026-07-01 14:00:00");
    when(databursatil.getQuotes(List.of("AMXL*"), "BMV"))
        .thenReturn(Mono.just(Map.of("AMXL*", quote)));

    HybridPriceQuote result = hybridQuoteService.getCurrentPrice("AMXL", "MXN", "BMV");

    assertThat(result.symbol()).isEqualTo("AMXL");
    assertThat(result.currency()).isEqualTo("MXN");
    assertThat(result.provider()).isEqualTo("DATABURSATIL");
    assertThat(result.price()).isEqualByComparingTo("11.25");
    verify(databursatil).getQuotes(List.of("AMXL*"), "BMV");
    verify(snapshotRepository).save(any());
    verifyNoInteractions(stockQuoteOrchestrator);
  }

  // -------------------------------------------------------------------------
  // C4b — USD usa el orquestador existente, no DataBursatil
  // -------------------------------------------------------------------------

  @Test
  void getCurrentPriceForUsdUsesExistingStockQuoteOrchestrator() {
    when(stockQuoteOrchestrator.getLatest("AAPL"))
        .thenReturn(Optional.of(new BigDecimal("150.25")));

    HybridPriceQuote result = hybridQuoteService.getCurrentPrice("AAPL", "USD", "NASDAQ");

    assertThat(result.symbol()).isEqualTo("AAPL");
    assertThat(result.currency()).isEqualTo("USD");
    assertThat(result.provider()).isEqualTo("orchestrator");
    assertThat(result.price()).isEqualByComparingTo("150.25");
    verify(stockQuoteOrchestrator).getLatest("AAPL");
    verifyNoInteractions(databursatil);
  }

  // -------------------------------------------------------------------------
  // C4c — tipo de cambio USD/MXN
  // -------------------------------------------------------------------------

  @Test
  void getUsdMxnRateReturnsRateAndPersistsFxSnapshot() {
    BmvFxQuote fx = new BmvFxQuote(17.5249, 0.09, 0.0149, "2026-06-26 02:31:00");
    when(databursatil.getFxRate("USDMXN")).thenReturn(Mono.just(fx));

    BigDecimal rate = hybridQuoteService.getUsdMxnRate();

    assertThat(rate).isEqualByComparingTo("17.5249");

    ArgumentCaptor<MarketFxSnapshotEntity> captor =
        ArgumentCaptor.forClass(MarketFxSnapshotEntity.class);
    verify(fxSnapshotRepository).save(captor.capture());
    MarketFxSnapshotEntity saved = captor.getValue();
    assertThat(saved.getTicker()).isEqualTo("USDMXN");
    assertThat(saved.getRate()).isEqualByComparingTo("17.5249");
    assertThat(saved.getProvider()).isEqualTo("DATABURSATIL");
  }

  // -------------------------------------------------------------------------
  // C4d — refresco batch en lotes de maximo 50
  // -------------------------------------------------------------------------

  @Test
  void refreshAllMxnPortfoliosBatchesInGroupsOfAtMost50() {
    List<String> symbols = IntStream.range(0, 80).mapToObj(i -> "SYM" + i).toList();
    when(mxnSymbolLookupPort.findDistinctMxnSymbols()).thenReturn(symbols);
    when(databursatil.getQuotes(anyList(), eq("BMV"))).thenReturn(Mono.just(Map.of()));

    hybridQuoteService.refreshAllMxnPortfolios();

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
    verify(databursatil, times(2)).getQuotes(captor.capture(), eq("BMV"));
    List<List<String>> batches = captor.getAllValues();
    assertThat(batches.get(0)).hasSize(50);
    assertThat(batches.get(1)).hasSize(30);
  }

  @Test
  void refreshAllMxnPortfoliosDoesNothingWhenNoMxnSymbolsExist() {
    when(mxnSymbolLookupPort.findDistinctMxnSymbols()).thenReturn(List.of());

    hybridQuoteService.refreshAllMxnPortfolios();

    verify(databursatil, never()).getQuotes(any(), any());
  }
}
