package com.mx.cryptomonitor.unit.marketdata.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mx.cryptomonitor.marketdata.application.port.out.DividendDetectedEvent;
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
  @Mock private org.springframework.context.ApplicationEventPublisher eventPublisher;

  private DividendSyncService service() {
    return new DividendSyncService(
        provider, corporateActionRepository, detailRepository, eventPublisher);
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

  private CorporateActionEntity storedDividend(
      LocalDate ex, LocalDate pay, CorporateActionStatus status, String providerId) {
    return CorporateActionEntity.builder()
        .id(UUID.randomUUID())
        .assetSymbol("GOOGL")
        .actionType(CorporateActionType.DIVIDEND)
        .status(status)
        .pivotalDate(ex)
        .paymentDate(pay)
        .sourceProvider("MASSIVE")
        .providerEventId(providerId)
        .syncedAt(OffsetDateTime.now(ZoneOffset.UTC))
        .build();
  }

  @Test
  void insertsNewPaidDividendAsCompletedWithDetail() {
    when(provider.fetchDividends("GOOGL")).thenReturn(List.of(paidGoogl()));
    when(corporateActionRepository.findByProviderEventId("E1cdc29fc58066"))
        .thenReturn(Optional.empty());
    when(corporateActionRepository.findByAssetSymbolAndActionTypeAndPivotalDate(
            "GOOGL", CorporateActionType.DIVIDEND, LocalDate.of(2024, 6, 10)))
        .thenReturn(Optional.empty());
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

    ArgumentCaptor<DividendDetectedEvent> eventCaptor =
        ArgumentCaptor.forClass(DividendDetectedEvent.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getValue().assetSymbol()).isEqualTo("GOOGL");
    assertThat(eventCaptor.getValue().dividendPerShare()).isEqualByComparingTo("0.20");
  }

  @Test
  void futureDividendIsAnnounced() {
    LocalDate ex = LocalDate.now(ZoneOffset.UTC).plusMonths(2);
    DividendEventData future =
        new DividendEventData(
            "AMZN",
            ex,
            ex,
            ex.plusDays(7),
            ex.minusMonths(1),
            new BigDecimal("0.25"),
            "USD",
            (short) 4,
            "recurring",
            null,
            null,
            "FUT1");
    when(provider.fetchDividends("AMZN")).thenReturn(List.of(future));
    when(corporateActionRepository.findByProviderEventId("FUT1")).thenReturn(Optional.empty());
    when(corporateActionRepository.findByAssetSymbolAndActionTypeAndPivotalDate(
            "AMZN", CorporateActionType.DIVIDEND, ex))
        .thenReturn(Optional.empty());
    when(corporateActionRepository.save(any(CorporateActionEntity.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    service().sync("AMZN");

    ArgumentCaptor<CorporateActionEntity> captor =
        ArgumentCaptor.forClass(CorporateActionEntity.class);
    verify(corporateActionRepository).save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo(CorporateActionStatus.ANNOUNCED);
  }

  /**
   * ADR-0020 (freeze-al-pago): un dividendo ya pagado es inmutable; aunque el feed traiga un monto
   * distinto (dato corrupto), no se re-escribe nada.
   */
  @Test
  void skipsFrozenDividendFoundByProviderId() {
    CorporateActionEntity frozen =
        storedDividend(
            LocalDate.of(2024, 6, 10),
            LocalDate.of(2024, 6, 17),
            CorporateActionStatus.COMPLETED,
            "E1cdc29fc58066");
    DividendEventData corrupted =
        new DividendEventData(
            "GOOGL",
            LocalDate.of(2024, 6, 10),
            LocalDate.of(2024, 6, 10),
            LocalDate.of(2024, 6, 17),
            LocalDate.of(2024, 4, 25),
            new BigDecimal("567.97"),
            "USD",
            (short) 4,
            "recurring",
            new BigDecimal("567.97"),
            new BigDecimal("0.99"),
            "E1cdc29fc58066");
    when(provider.fetchDividends("GOOGL")).thenReturn(List.of(corrupted));
    when(corporateActionRepository.findByProviderEventId("E1cdc29fc58066"))
        .thenReturn(Optional.of(frozen));

    int created = service().sync("GOOGL");

    assertThat(created).isZero();
    verify(corporateActionRepository, never()).save(any());
    verify(detailRepository, never()).save(any());
    verify(eventPublisher, never()).publishEvent(any());
  }

  /** Dedup/freeze por llave natural cuando el proveedor no entrega id. */
  @Test
  void skipsFrozenDividendFoundByNaturalKey() {
    CorporateActionEntity frozen =
        storedDividend(
            LocalDate.of(2024, 6, 10),
            LocalDate.of(2024, 6, 17),
            CorporateActionStatus.COMPLETED,
            null);
    DividendEventData noId =
        new DividendEventData(
            "GOOGL",
            LocalDate.of(2024, 6, 10),
            LocalDate.of(2024, 6, 10),
            LocalDate.of(2024, 6, 17),
            null,
            new BigDecimal("0.20"),
            "USD",
            (short) 4,
            "recurring",
            null,
            null,
            null);
    when(provider.fetchDividends("GOOGL")).thenReturn(List.of(noId));
    when(corporateActionRepository.findByAssetSymbolAndActionTypeAndPivotalDate(
            "GOOGL", CorporateActionType.DIVIDEND, LocalDate.of(2024, 6, 10)))
        .thenReturn(Optional.of(frozen));

    int created = service().sync("GOOGL");

    assertThat(created).isZero();
    verify(corporateActionRepository, never()).save(any());
    verify(detailRepository, never()).save(any());
  }

  /**
   * ADR-0020: un dividendo provisional (aun no pagado) SI se actualiza si el emisor revisa el
   * monto.
   */
  @Test
  void updatesProvisionalDividendWhenRevised() {
    LocalDate ex = LocalDate.now(ZoneOffset.UTC).plusMonths(2);
    LocalDate pay = ex.plusDays(7);
    CorporateActionEntity provisional =
        storedDividend(ex, pay, CorporateActionStatus.ANNOUNCED, "PROV1");
    DividendActionDetailEntity detail =
        DividendActionDetailEntity.builder()
            .corporateActionId(provisional.getId())
            .amountPerShare(new BigDecimal("0.20"))
            .currency("USD")
            .frequency((short) 4)
            .distributionType(DividendDistributionType.RECURRING)
            .splitAdjustedDps(new BigDecimal("0.20"))
            .priceAdjustmentFactor(new BigDecimal("0.99"))
            .build();
    DividendEventData revised =
        new DividendEventData(
            "GOOGL",
            ex,
            ex,
            pay,
            ex.minusMonths(1),
            new BigDecimal("0.25"),
            "USD",
            (short) 4,
            "recurring",
            new BigDecimal("0.25"),
            new BigDecimal("0.99"),
            "PROV1");
    when(provider.fetchDividends("GOOGL")).thenReturn(List.of(revised));
    when(corporateActionRepository.findByProviderEventId("PROV1"))
        .thenReturn(Optional.of(provisional));
    when(detailRepository.findById(provisional.getId())).thenReturn(Optional.of(detail));

    int created = service().sync("GOOGL");

    assertThat(created).isZero();
    verify(corporateActionRepository).save(provisional);
    ArgumentCaptor<DividendActionDetailEntity> savedDetail =
        ArgumentCaptor.forClass(DividendActionDetailEntity.class);
    verify(detailRepository).save(savedDetail.capture());
    assertThat(savedDetail.getValue().getAmountPerShare()).isEqualByComparingTo("0.25");
    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  void blankTickerIsNoOp() {
    int created = service().sync("  ");

    assertThat(created).isZero();
    verifyNoInteractions(provider, corporateActionRepository, detailRepository);
  }
}
