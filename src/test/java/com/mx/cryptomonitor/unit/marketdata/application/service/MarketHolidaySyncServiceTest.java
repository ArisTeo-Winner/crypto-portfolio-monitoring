package com.mx.cryptomonitor.unit.marketdata.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mx.cryptomonitor.marketdata.application.port.out.MarketHolidayData;
import com.mx.cryptomonitor.marketdata.application.port.out.MarketHolidayProviderPort;
import com.mx.cryptomonitor.marketdata.application.service.MarketHolidaySyncService;
import com.mx.cryptomonitor.marketdata.domain.model.Market;
import com.mx.cryptomonitor.marketdata.domain.model.MarketHolidayEntity;
import com.mx.cryptomonitor.marketdata.domain.repository.MarketHolidayRepository;

@ExtendWith(MockitoExtension.class)
class MarketHolidaySyncServiceTest {

  @Mock private MarketHolidayProviderPort primary;
  @Mock private MarketHolidayProviderPort fallback;
  @Mock private MarketHolidayRepository repository;

  @Test
  void usesFallbackWhenPrimaryEmptyAndInsertsOnlyMissingDates() {
    when(primary.fetchHolidays()).thenReturn(List.of());
    when(fallback.fetchHolidays())
        .thenReturn(
            List.of(
                new MarketHolidayData(
                    Market.US_EQUITY, LocalDate.of(2026, 12, 25), "Christmas", null),
                new MarketHolidayData(
                    Market.US_EQUITY, LocalDate.of(2027, 1, 1), "New Year", null)));
    when(repository.findByMarket("US_EQUITY"))
        .thenReturn(
            List.of(
                MarketHolidayEntity.builder()
                    .market("US_EQUITY")
                    .holidayDate(LocalDate.of(2026, 12, 25))
                    .name("Christmas Day")
                    .build()));

    MarketHolidaySyncService service =
        new MarketHolidaySyncService(List.of(primary, fallback), repository, true);
    int inserted = service.syncUsHolidays();

    assertThat(inserted).isEqualTo(1); // solo 2027-01-01 (2026-12-25 ya existe)
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<MarketHolidayEntity>> captor = ArgumentCaptor.forClass(List.class);
    verify(repository).saveAll(captor.capture());
    assertThat(captor.getValue()).hasSize(1);
    assertThat(captor.getValue().get(0).getHolidayDate()).isEqualTo(LocalDate.of(2027, 1, 1));
  }

  @Test
  void scheduledSyncIsNoOpWhenDisabled() {
    MarketHolidaySyncService service =
        new MarketHolidaySyncService(List.of(primary, fallback), repository, false);

    service.scheduledSync();

    verifyNoInteractions(primary, fallback);
    verify(repository, never()).saveAll(anyList());
  }
}
