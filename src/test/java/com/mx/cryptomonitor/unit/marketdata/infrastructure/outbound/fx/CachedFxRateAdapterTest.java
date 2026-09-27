package com.mx.cryptomonitor.unit.marketdata.infrastructure.outbound.fx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.mx.cryptomonitor.marketdata.application.port.out.UsdMxnRateDetail;
import com.mx.cryptomonitor.marketdata.application.service.HybridQuoteService;
import com.mx.cryptomonitor.marketdata.domain.exception.DataBursatilException;
import com.mx.cryptomonitor.marketdata.domain.model.MarketFxSnapshotEntity;
import com.mx.cryptomonitor.marketdata.domain.repository.MarketFxSnapshotRepository;
import com.mx.cryptomonitor.marketdata.infrastructure.outbound.fx.CachedFxRateAdapter;

@ExtendWith(MockitoExtension.class)
class CachedFxRateAdapterTest {

  private static final String USD_MXN_TICKER = "USDMXN";

  @Mock private MarketFxSnapshotRepository fxSnapshotRepository;
  @Mock private HybridQuoteService hybridQuoteService;

  @InjectMocks private CachedFxRateAdapter adapter;

  @BeforeEach
  void setTtl() {
    ReflectionTestUtils.setField(adapter, "cacheTtlMinutes", 60L);
  }

  private MarketFxSnapshotEntity snapshotQuotedMinutesAgo(BigDecimal rate, long minutesAgo) {
    return MarketFxSnapshotEntity.builder()
        .ticker(USD_MXN_TICKER)
        .rate(rate)
        .quoteAt(OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(minutesAgo))
        .build();
  }

  @Test
  void detailReturnsRateProviderAndAsOfFromFreshSnapshot() {
    OffsetDateTime asOf = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(10);
    MarketFxSnapshotEntity snapshot =
        MarketFxSnapshotEntity.builder()
            .ticker(USD_MXN_TICKER)
            .rate(new BigDecimal("17.5147"))
            .provider("BANXICO")
            .quoteAt(asOf)
            .build();
    when(fxSnapshotRepository.findFirstByTickerOrderByQuoteAtDesc(USD_MXN_TICKER))
        .thenReturn(Optional.of(snapshot));

    Optional<UsdMxnRateDetail> detail = adapter.usdMxnRateDetail();

    assertThat(detail).isPresent();
    assertThat(detail.get().rate()).isEqualByComparingTo("17.5147");
    assertThat(detail.get().provider()).isEqualTo("BANXICO");
    assertThat(detail.get().asOf()).isEqualTo(asOf);
    verify(hybridQuoteService, never()).getUsdMxnRate();
  }

  @Test
  void freshCacheIsServedWithoutLiveCall() {
    when(fxSnapshotRepository.findFirstByTickerOrderByQuoteAtDesc(USD_MXN_TICKER))
        .thenReturn(Optional.of(snapshotQuotedMinutesAgo(new BigDecimal("17.5147"), 10)));

    Optional<BigDecimal> rate = adapter.usdMxnRate();

    assertThat(rate).contains(new BigDecimal("17.5147"));
    verify(hybridQuoteService, never()).getUsdMxnRate();
  }

  @Test
  void emptyCacheTriggersLiveRefresh() {
    when(fxSnapshotRepository.findFirstByTickerOrderByQuoteAtDesc(USD_MXN_TICKER))
        .thenReturn(Optional.empty());
    when(hybridQuoteService.getUsdMxnRate()).thenReturn(new BigDecimal("18.1000"));

    Optional<BigDecimal> rate = adapter.usdMxnRate();

    assertThat(rate).contains(new BigDecimal("18.1000"));
    verify(hybridQuoteService).getUsdMxnRate();
  }

  @Test
  void staleCacheTriggersLiveRefresh() {
    when(fxSnapshotRepository.findFirstByTickerOrderByQuoteAtDesc(USD_MXN_TICKER))
        .thenReturn(Optional.of(snapshotQuotedMinutesAgo(new BigDecimal("17.0000"), 120)));
    when(hybridQuoteService.getUsdMxnRate()).thenReturn(new BigDecimal("18.1000"));

    Optional<BigDecimal> rate = adapter.usdMxnRate();

    assertThat(rate).contains(new BigDecimal("18.1000"));
    verify(hybridQuoteService).getUsdMxnRate();
  }

  @Test
  void liveFailureFallsBackToStaleCache() {
    when(fxSnapshotRepository.findFirstByTickerOrderByQuoteAtDesc(USD_MXN_TICKER))
        .thenReturn(Optional.of(snapshotQuotedMinutesAgo(new BigDecimal("17.0000"), 120)));
    when(hybridQuoteService.getUsdMxnRate())
        .thenThrow(new DataBursatilException("proveedor caido"));

    Optional<BigDecimal> rate = adapter.usdMxnRate();

    assertThat(rate).contains(new BigDecimal("17.0000"));
  }

  @Test
  void liveFailureWithNoCacheReturnsEmpty() {
    when(fxSnapshotRepository.findFirstByTickerOrderByQuoteAtDesc(USD_MXN_TICKER))
        .thenReturn(Optional.empty());
    when(hybridQuoteService.getUsdMxnRate())
        .thenThrow(new DataBursatilException("proveedor caido"));

    Optional<BigDecimal> rate = adapter.usdMxnRate();

    assertThat(rate).isEmpty();
    verify(fxSnapshotRepository).findFirstByTickerOrderByQuoteAtDesc(anyString());
  }
}
