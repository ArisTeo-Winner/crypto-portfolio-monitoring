package com.mx.cryptomonitor.unit.marketdata.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.mx.cryptomonitor.marketdata.application.port.out.MergerDetectedEvent;
import com.mx.cryptomonitor.marketdata.application.service.RegisterMergerService;
import com.mx.cryptomonitor.marketdata.domain.model.CorporateActionEntity;
import com.mx.cryptomonitor.marketdata.domain.model.CorporateActionStatus;
import com.mx.cryptomonitor.marketdata.domain.model.CorporateActionType;
import com.mx.cryptomonitor.marketdata.domain.model.MergerActionDetailEntity;
import com.mx.cryptomonitor.marketdata.domain.model.MergerType;
import com.mx.cryptomonitor.marketdata.domain.repository.CorporateActionRepository;
import com.mx.cryptomonitor.marketdata.domain.repository.MergerActionDetailRepository;

@ExtendWith(MockitoExtension.class)
class RegisterMergerServiceTest {

  @Mock private CorporateActionRepository corporateActionRepository;
  @Mock private MergerActionDetailRepository mergerDetailRepository;
  @Mock private ApplicationEventPublisher eventPublisher;

  private RegisterMergerService service() {
    return new RegisterMergerService(
        corporateActionRepository, mergerDetailRepository, eventPublisher);
  }

  private static final LocalDate EFFECTIVE = LocalDate.of(2024, 3, 1);
  private static final String EVENT_ID = "merger:CEPT:2024-03-01";

  @Test
  void persistsEventAndDetailAndPublishesWhenNew() {
    when(corporateActionRepository.findByProviderEventId(EVENT_ID)).thenReturn(Optional.empty());
    UUID generated = UUID.randomUUID();
    when(corporateActionRepository.save(any(CorporateActionEntity.class)))
        .thenAnswer(
            inv -> {
              CorporateActionEntity e = inv.getArgument(0);
              e.setId(generated);
              return e;
            });

    UUID id =
        service()
            .registerMerger(
                "cept",
                "secz",
                MergerType.ALL_STOCK,
                new BigDecimal("1.0"),
                null,
                "USD",
                EFFECTIVE);

    assertThat(id).isEqualTo(generated);

    ArgumentCaptor<CorporateActionEntity> caCaptor =
        ArgumentCaptor.forClass(CorporateActionEntity.class);
    verify(corporateActionRepository).save(caCaptor.capture());
    assertThat(caCaptor.getValue().getAssetSymbol()).isEqualTo("CEPT");
    assertThat(caCaptor.getValue().getActionType()).isEqualTo(CorporateActionType.MERGER);
    assertThat(caCaptor.getValue().getStatus()).isEqualTo(CorporateActionStatus.COMPLETED);
    assertThat(caCaptor.getValue().getProviderEventId()).isEqualTo(EVENT_ID);

    ArgumentCaptor<MergerActionDetailEntity> detailCaptor =
        ArgumentCaptor.forClass(MergerActionDetailEntity.class);
    verify(mergerDetailRepository).save(detailCaptor.capture());
    assertThat(detailCaptor.getValue().getMergerType()).isEqualTo(MergerType.ALL_STOCK);
    assertThat(detailCaptor.getValue().getSuccessorSymbol()).isEqualTo("SECZ");
    assertThat(detailCaptor.getValue().getExchangeRatio()).isEqualByComparingTo("1.0");

    ArgumentCaptor<MergerDetectedEvent> eventCaptor =
        ArgumentCaptor.forClass(MergerDetectedEvent.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());
    assertThat(eventCaptor.getValue().predecessorSymbol()).isEqualTo("CEPT");
    assertThat(eventCaptor.getValue().successorSymbol()).isEqualTo("SECZ");
    assertThat(eventCaptor.getValue().effectiveDate()).isEqualTo(EFFECTIVE);
  }

  @Test
  void idempotentWhenAlreadyRegisteredDoesNotPersistOrPublish() {
    CorporateActionEntity existing = new CorporateActionEntity();
    UUID existingId = UUID.randomUUID();
    existing.setId(existingId);
    when(corporateActionRepository.findByProviderEventId(EVENT_ID))
        .thenReturn(Optional.of(existing));

    UUID id =
        service()
            .registerMerger(
                "CEPT",
                "SECZ",
                MergerType.ALL_STOCK,
                new BigDecimal("1.0"),
                null,
                "USD",
                EFFECTIVE);

    assertThat(id).isEqualTo(existingId);
    verify(corporateActionRepository, never()).save(any());
    verify(mergerDetailRepository, never()).save(any());
    verify(eventPublisher, never()).publishEvent(any());
  }
}
