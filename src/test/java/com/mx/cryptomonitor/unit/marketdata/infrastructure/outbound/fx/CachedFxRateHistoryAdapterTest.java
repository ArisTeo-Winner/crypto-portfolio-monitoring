package com.mx.cryptomonitor.unit.marketdata.infrastructure.outbound.fx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mx.cryptomonitor.marketdata.application.port.out.FxRatePort;
import com.mx.cryptomonitor.marketdata.application.port.out.UsdMxnFxRateHistoryProviderPort;
import com.mx.cryptomonitor.marketdata.domain.model.FxRateDailyEntity;
import com.mx.cryptomonitor.marketdata.domain.repository.FxRateDailyRepository;
import com.mx.cryptomonitor.marketdata.infrastructure.outbound.fx.CachedFxRateHistoryAdapter;

@ExtendWith(MockitoExtension.class)
class CachedFxRateHistoryAdapterTest {

  private static final String USD_MXN_TICKER = "USDMXN";

  @Mock private FxRateDailyRepository fxRateDailyRepository;
  @Mock private UsdMxnFxRateHistoryProviderPort fxRateHistoryProvider;
  @Mock private FxRatePort fxRatePort;

  @InjectMocks private CachedFxRateHistoryAdapter adapter;

  private FxRateDailyEntity entity(LocalDate date, String rate) {
    return FxRateDailyEntity.builder()
        .ticker(USD_MXN_TICKER)
        .rateDate(date)
        .rate(new BigDecimal(rate))
        .build();
  }

  @Test
  void nullDateReturnsEmpty() {
    assertThat(adapter.usdMxnRateOn(null)).isEmpty();
    verifyNoInteractions(fxRateHistoryProvider, fxRatePort);
  }

  @Test
  void servesCachedRateWithoutFetchWhenCovered() {
    LocalDate date = LocalDate.of(2025, 6, 12);
    when(fxRateDailyRepository.findByTickerAndRateDateBetween(eq(USD_MXN_TICKER), any(), any()))
        .thenReturn(List.of(entity(date, "18.9068")));
    when(fxRateDailyRepository.findFirstByTickerAndRateDateLessThanEqualOrderByRateDateDesc(
            USD_MXN_TICKER, date))
        .thenReturn(Optional.of(entity(date, "18.9068")));

    Optional<BigDecimal> rate = adapter.usdMxnRateOn(date);

    assertThat(rate).contains(new BigDecimal("18.9068"));
    verifyNoInteractions(fxRateHistoryProvider, fxRatePort);
  }

  @Test
  void fetchesPersistsAndReturnsOnCacheMiss() {
    LocalDate date = LocalDate.of(2025, 6, 12);
    when(fxRateDailyRepository.findByTickerAndRateDateBetween(eq(USD_MXN_TICKER), any(), any()))
        .thenReturn(List.of());
    when(fxRateHistoryProvider.fetchUsdMxnRates(any(), any()))
        .thenReturn(Map.of(date, new BigDecimal("18.9068")));
    when(fxRateDailyRepository.findFirstByTickerAndRateDateLessThanEqualOrderByRateDateDesc(
            USD_MXN_TICKER, date))
        .thenReturn(Optional.of(entity(date, "18.9068")));

    Optional<BigDecimal> rate = adapter.usdMxnRateOn(date);

    assertThat(rate).contains(new BigDecimal("18.9068"));
    verify(fxRateHistoryProvider).fetchUsdMxnRates(any(), any());
    verify(fxRateDailyRepository).saveAll(anyList());
    verifyNoInteractions(fxRatePort);
  }

  @Test
  void fallsBackToSpotRateWhenNoHistoricalAvailable() {
    LocalDate date = LocalDate.of(2025, 6, 12);
    when(fxRateDailyRepository.findByTickerAndRateDateBetween(eq(USD_MXN_TICKER), any(), any()))
        .thenReturn(List.of());
    when(fxRateHistoryProvider.fetchUsdMxnRates(any(), any())).thenReturn(Map.of());
    when(fxRateDailyRepository.findFirstByTickerAndRateDateLessThanEqualOrderByRateDateDesc(
            USD_MXN_TICKER, date))
        .thenReturn(Optional.empty());
    when(fxRatePort.usdMxnRate()).thenReturn(Optional.of(new BigDecimal("17.5147")));

    Optional<BigDecimal> rate = adapter.usdMxnRateOn(date);

    assertThat(rate).contains(new BigDecimal("17.5147"));
  }
}
