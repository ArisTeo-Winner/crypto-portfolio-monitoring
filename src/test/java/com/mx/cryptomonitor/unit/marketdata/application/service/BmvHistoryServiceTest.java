package com.mx.cryptomonitor.unit.marketdata.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mx.cryptomonitor.marketdata.application.port.out.BmvMarketDataPort;
import com.mx.cryptomonitor.marketdata.application.service.BmvHistoryService;
import com.mx.cryptomonitor.marketdata.domain.model.BmvHistoricalPoint;
import com.mx.cryptomonitor.marketdata.domain.model.BmvPriceHistoryEntity;
import com.mx.cryptomonitor.marketdata.domain.repository.BmvPriceHistoryRepository;

import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class BmvHistoryServiceTest {

  private static final String SYMBOL = "AMXL*";

  @Mock private BmvMarketDataPort databursatil;
  @Mock private BmvPriceHistoryRepository historyRepository;

  @InjectMocks private BmvHistoryService bmvHistoryService;

  @Test
  void firstCallWithNothingCachedDownloadsFullRangeAndCaches() {
    LocalDate from = LocalDate.of(2026, 1, 1);
    LocalDate to = LocalDate.of(2026, 1, 5);
    BmvHistoricalPoint point =
        new BmvHistoricalPoint(to, new BigDecimal("10.50"), new BigDecimal("1000.00"));
    BmvPriceHistoryEntity persisted = entityFor(to, point);

    when(historyRepository.findByEmisoraSerieAndTradeDateBetween(SYMBOL, from, to))
        .thenReturn(List.of())
        .thenReturn(List.of(persisted));
    when(databursatil.getHistory(SYMBOL, from, to)).thenReturn(Mono.just(Map.of(to, point)));

    List<BmvHistoricalPoint> result = bmvHistoryService.getHistory(SYMBOL, from, to);

    verify(databursatil).getHistory(SYMBOL, from, to);
    verify(historyRepository)
        .upsert(
            any(UUID.class),
            eq(SYMBOL),
            eq(to),
            eq(point.closePrice()),
            eq(point.amountTraded()),
            eq("DATABURSATIL"),
            any(OffsetDateTime.class));
    assertThat(result).containsExactly(point);
  }

  @Test
  void secondCallWithRangeFullyCachedMakesNoCallsToDataBursatil() {
    LocalDate from = LocalDate.of(2026, 1, 1);
    LocalDate to = LocalDate.of(2026, 1, 5);
    BmvHistoricalPoint point =
        new BmvHistoricalPoint(to, new BigDecimal("11.00"), new BigDecimal("2000.00"));
    BmvPriceHistoryEntity persisted = entityFor(to, point);

    when(historyRepository.findByEmisoraSerieAndTradeDateBetween(SYMBOL, from, to))
        .thenReturn(List.of(persisted));

    List<BmvHistoricalPoint> result = bmvHistoryService.getHistory(SYMBOL, from, to);

    verify(databursatil, never()).getHistory(any(), any(), any());
    assertThat(result).containsExactly(point);
  }

  @Test
  void partiallyCachedRangeOnlyFetchesTheMissingGap() {
    LocalDate from = LocalDate.of(2026, 1, 1);
    LocalDate lastCached = LocalDate.of(2026, 1, 3);
    LocalDate to = LocalDate.of(2026, 1, 5);
    BmvHistoricalPoint cachedPoint =
        new BmvHistoricalPoint(lastCached, new BigDecimal("9.00"), new BigDecimal("500.00"));
    BmvHistoricalPoint gapPoint =
        new BmvHistoricalPoint(to, new BigDecimal("9.50"), new BigDecimal("600.00"));
    BmvPriceHistoryEntity cachedEntity = entityFor(lastCached, cachedPoint);
    BmvPriceHistoryEntity gapEntity = entityFor(to, gapPoint);

    when(historyRepository.findByEmisoraSerieAndTradeDateBetween(SYMBOL, from, to))
        .thenReturn(List.of(cachedEntity))
        .thenReturn(List.of(cachedEntity, gapEntity));
    when(databursatil.getHistory(SYMBOL, lastCached.plusDays(1), to))
        .thenReturn(Mono.just(Map.of(to, gapPoint)));

    List<BmvHistoricalPoint> result = bmvHistoryService.getHistory(SYMBOL, from, to);

    verify(databursatil).getHistory(SYMBOL, lastCached.plusDays(1), to);
    verify(databursatil, never()).getHistory(eq(SYMBOL), eq(from), any());
    assertThat(result).containsExactly(cachedPoint, gapPoint);
  }

  private BmvPriceHistoryEntity entityFor(LocalDate date, BmvHistoricalPoint point) {
    return BmvPriceHistoryEntity.builder()
        .id(UUID.randomUUID())
        .emisoraSerie(SYMBOL)
        .tradeDate(date)
        .closePrice(point.closePrice())
        .amountTraded(point.amountTraded())
        .provider("DATABURSATIL")
        .cachedAt(OffsetDateTime.now(ZoneOffset.UTC))
        .build();
  }
}
