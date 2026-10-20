package com.mx.cryptomonitor.unit.marketdata.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mx.cryptomonitor.marketdata.application.port.out.DividendEventData;
import com.mx.cryptomonitor.marketdata.application.port.out.DividendProviderPort;
import com.mx.cryptomonitor.marketdata.application.service.DividendSyncService;
import com.mx.cryptomonitor.marketdata.domain.model.CorporateActionEntity;
import com.mx.cryptomonitor.marketdata.domain.model.CorporateActionStatus;
import com.mx.cryptomonitor.marketdata.domain.model.CorporateActionType;
import com.mx.cryptomonitor.marketdata.domain.model.DividendActionDetailEntity;
import com.mx.cryptomonitor.marketdata.domain.model.DividendDistributionType;
import com.mx.cryptomonitor.marketdata.domain.repository.CorporateActionRepository;
import com.mx.cryptomonitor.marketdata.domain.repository.DividendActionDetailRepository;

@ExtendWith(MockitoExtension.class)
class DividendSyncServiceTest {

  @Mock private DividendProviderPort provider;
  @Mock private CorporateActionRepository corporateActionRepository;
  @Mock private DividendActionDetailRepository detailRepository;

  private DividendSyncService service() {
    return new DividendSyncService(provider, corporateActionRepository, detailRepository);
  }

  private DividendEventData paidGoogl() {
    return new DividendEventData(
        "GOOGL",
        LocalDate.of(2024, 6, 10),
        LocalDate.of(2024, 6, 10),
        LocalDate.of(2024, 6, 17),
        LocalDate.of(2024, 4, 25),
        new BigDecimal("0.20"),
        "USD",
        (short) 4,
        "recurring",
        new BigDecimal("0.20"),
        new BigDecimal("0.994165"),
        "E1cdc29fc58066");
  }

  @Test
  void insertsNewPaidDividendAsCompletedWithDetail() {
    when(provider.fetchDividends("GOOGL")).thenReturn(List.of(paidGoogl()));
    when(corporateActionRepository.findByProviderEventId("E1cdc29fc58066"))
        .thenReturn(Optional.empty());
    when(corporateActionRepository.existsByAssetSymbolAndActionTypeAndPivotalDate(
            "GOOGL", CorporateActionType.DIVIDEND, LocalDate.of(2024, 6, 10)))
        .thenReturn(false);
    when(corporateActionRepository.save(any(CorporateActionEntity.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    int created = service().sync("googl");

    assertThat(created).isEqualTo(1);

    ArgumentCaptor<CorporateActionEntity> actionCaptor =
        ArgumentCaptor.forClass(CorporateActionEntity.class);
    verify(corporateActionRepository).save(actionCaptor.capture());
    CorporateActionEntity action = actionCaptor.getValue();
    assertThat(action.getAssetSymbol()).isEqualTo("GOOGL");
    assertThat(action.getActionType()).isEqualTo(CorporateActionType.DIVIDEND);
    assertThat(action.getStatus()).isEqualTo(CorporateActionStatus.COMPLETED);
    assertThat(action.getPivotalDate()).isEqualTo(LocalDate.of(2024, 6, 10));
    assertThat(action.getSourceProvider()).isEqualTo("MASSIVE");

    ArgumentCaptor<DividendActionDetailEntity> detailCaptor =
        ArgumentCaptor.forClass(DividendActionDetailEntity.class);
    verify(detailRepository).save(detailCaptor.capture());
    DividendActionDetailEntity detail = detailCaptor.getValue();
    assertThat(detail.getAmountPerShare()).isEqualByComparingTo("0.20");
    assertThat(detail.getCurrency()).isEqualTo("USD");
    assertThat(detail.getFrequency()).isEqualTo((short) 4);
    assertThat(detail.getDistributionType()).isEqualTo(DividendDistributionType.RECURRING);
  }

  @Test
  void futureDividendIsAnnounced() {
    DividendEventData future =
        new DividendEventData(
            "AMZN",
            LocalDate.now().plusMonths(2),
            LocalDate.now().plusMonths(2),
            LocalDate.now().plusMonths(2).plusDays(7),
            LocalDate.now().plusMonths(1),
            new BigDecimal("0.25"),
            "USD",
            (short) 4,
            "recurring",
            null,
            null,
            "FUT1");
    when(provider.fetchDividends("AMZN")).thenReturn(List.of(future));
    when(corporateActionRepository.findByProviderEventId("FUT1")).thenReturn(Optional.empty());
    when(corporateActionRepository.existsByAssetSymbolAndActionTypeAndPivotalDate(
            any(), any(), any()))
        .thenReturn(false);
    when(corporateActionRepository.save(any(CorporateActionEntity.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    service().sync("AMZN");

    ArgumentCaptor<CorporateActionEntity> captor =
        ArgumentCaptor.forClass(CorporateActionEntity.class);
    verify(corporateActionRepository).save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(CorporateActionStatus.ANNOUNCED);
  }

  @Test
  void skipsWhenProviderEventIdAlreadyStored() {
    when(provider.fetchDividends("GOOGL")).thenReturn(List.of(paidGoogl()));
    when(corporateActionRepository.findByProviderEventId("E1cdc29fc58066"))
        .thenReturn(Optional.of(new CorporateActionEntity()));

    int created = service().sync("GOOGL");

    assertThat(created).isZero();
    verify(corporateActionRepository, never()).save(any());
    verify(detailRepository, never()).save(any());
  }

  @Test
  void skipsDuplicateByNaturalKeyWhenNoProviderId() {
    DividendEventData noId =
        new DividendEventData(
            "GOOGL",
            LocalDate.of(2024, 6, 10),
            null,
            null,
            null,
            new BigDecimal("0.20"),
            "USD",
            (short) 4,
            "recurring",
            null,
            null,
            null);
    when(provider.fetchDividends("GOOGL")).thenReturn(List.of(noId));
    when(corporateActionRepository.existsByAssetSymbolAndActionTypeAndPivotalDate(
            "GOOGL", CorporateActionType.DIVIDEND, LocalDate.of(2024, 6, 10)))
        .thenReturn(true);

    int created = service().sync("GOOGL");

    assertThat(created).isZero();
    verify(corporateActionRepository, never()).save(any());
  }

  @Test
  void blankTickerIsNoOp() {
    int created = service().sync("  ");

    assertThat(created).isZero();
    verifyNoInteractions(provider, corporateActionRepository, detailRepository);
  }
}
