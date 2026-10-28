package com.mx.cryptomonitor.unit.marketdata.infrastructure.outbound.split;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.mx.cryptomonitor.marketdata.application.port.out.StockSplitData;
import com.mx.cryptomonitor.marketdata.application.port.out.StockSplitProviderPort;
import com.mx.cryptomonitor.marketdata.domain.model.StockSplitEntity;
import com.mx.cryptomonitor.marketdata.domain.model.StockSplitSyncEntity;
import com.mx.cryptomonitor.marketdata.domain.repository.StockSplitRepository;
import com.mx.cryptomonitor.marketdata.domain.repository.StockSplitSyncRepository;
import com.mx.cryptomonitor.marketdata.infrastructure.outbound.split.CachedStockSplitAdapter;

@ExtendWith(MockitoExtension.class)
class CachedStockSplitAdapterTest {

  @Mock private StockSplitRepository stockSplitRepository;
  @Mock private StockSplitSyncRepository stockSplitSyncRepository;
  @Mock private StockSplitProviderPort stockSplitProvider;

  @InjectMocks private CachedStockSplitAdapter adapter;

  @BeforeEach
  void setUp() {
    ReflectionTestUtils.setField(adapter, "cacheTtlHours", 24L);
  }

  @Test
  void fetchesFromProviderOnCacheMissAndPersists() {
    when(stockSplitSyncRepository.findByTicker("WETO")).thenReturn(Optional.empty());
    when(stockSplitProvider.fetchSplits("WETO"))
        .thenReturn(
            List.of(new StockSplitData("WETO", LocalDate.of(2026, 8, 3), new BigDecimal("0.01"))));
    when(stockSplitRepository.findByTickerOrderByExecutionDateDesc("WETO"))
        .thenReturn(List.of()) // persistMissing: nothing stored yet
        .thenReturn(
            List.of(
                StockSplitEntity.builder()
                    .ticker("WETO")
                    .executionDate(LocalDate.of(2026, 8, 3))
                    .shareMultiplier(new BigDecimal("0.01"))
                    .provider("MASSIVE")
                    .build()));

    List<StockSplitData> result = adapter.splitsFor("weto");

    assertThat(result).hasSize(1);
    assertThat(result.get(0).ticker()).isEqualTo("WETO");
    assertThat(result.get(0).shareMultiplier()).isEqualByComparingTo("0.01");

    ArgumentCaptor<List<StockSplitEntity>> captor = ArgumentCaptor.forClass(List.class);
    verify(stockSplitRepository).saveAll(captor.capture());
    assertThat(captor.getValue()).hasSize(1);
    assertThat(captor.getValue().get(0).getShareMultiplier()).isEqualByComparingTo("0.01");
    verify(stockSplitSyncRepository)
        .save(org.mockito.ArgumentMatchers.any(StockSplitSyncEntity.class));
  }

  @Test
  void servesFromCacheWithinTtlWithoutCallingProvider() {
    when(stockSplitSyncRepository.findByTicker("WETO"))
        .thenReturn(
            Optional.of(
                StockSplitSyncEntity.builder()
                    .ticker("WETO")
                    .syncedAt(OffsetDateTime.now(ZoneOffset.UTC).minusHours(1))
                    .build()));
    when(stockSplitRepository.findByTickerOrderByExecutionDateDesc("WETO"))
        .thenReturn(
            List.of(
                StockSplitEntity.builder()
                    .ticker("WETO")
                    .executionDate(LocalDate.of(2026, 8, 3))
                    .shareMultiplier(new BigDecimal("0.01"))
                    .provider("MASSIVE")
                    .build()));

    List<StockSplitData> result = adapter.splitsFor("WETO");

    assertThat(result).hasSize(1);
    verify(stockSplitProvider, never()).fetchSplits("WETO");
    verify(stockSplitRepository, never()).saveAll(anyList());
  }

  @Test
  void returnsEmptyForBlankTicker() {
    assertThat(adapter.splitsFor("  ")).isEmpty();
    verify(stockSplitProvider, never()).fetchSplits(org.mockito.ArgumentMatchers.anyString());
  }
}
