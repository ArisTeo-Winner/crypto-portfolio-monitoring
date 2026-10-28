package com.mx.cryptomonitor.unit.marketdata.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

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

  @Mock private DividendProviderPort dividendProvider;
  @Mock private CorporateActionRepository corporateActionRepository;
  @Mock private DividendActionDetailRepository dividendDetailRepository;
  @Mock private ApplicationEventPublisher eventPublisher;
  @InjectMocks private DividendSyncService service;

  private static DividendEventData event(
      LocalDate ex, LocalDate pay, String amount, String providerId) {
    return new DividendEventData(
        "GOOGL",
        ex,
        ex,
        pay,
        ex.minusMonths(1),
        new BigDecimal(amount),
        "USD",
        (short) 4,
        "recurring",
        new BigDecimal(amount),
        new BigDecimal("0.99"),
        providerId);
  }

  private static CorporateActionEntity stored(
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

  private static DividendActionDetailEntity detail(CorporateActionEntity action, String amount) {
    return DividendActionDetailEntity.builder()
        .corporateActionId(action.getId())
        .amountPerShare(new BigDecimal(amount))
        .currency("USD")
        .frequency((short) 4)
        .distributionType(DividendDistributionType.RECURRING)
        .splitAdjustedDps(new BigDecimal(amount))
        .priceAdjustmentFactor(new BigDecimal("0.99"))
        .build();
  }

  @Test
  void insertaEventoNuevo() {
    LocalDate ex = LocalDate.now(ZoneOffset.UTC).minusMonths(1);
    LocalDate pay = ex.plusDays(7);
    when(dividendProvider.fetchDividends("GOOGL")).thenReturn(List.of(event(ex, pay, "0.21", "e1")));
    when(corporateActionRepository.findByProviderEventId("e1")).thenReturn(Optional.empty());
    when(corporateActionRepository.findByAssetSymbolAndActionTypeAndPivotalDate(
            "GOOGL", CorporateActionType.DIVIDEND, ex))
        .thenReturn(Optional.empty());
    when(corporateActionRepository.save(any(CorporateActionEntity.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    int created = service.sync("GOOGL");

    assertThat(created).isEqualTo(1);
    verify(corporateActionRepository).save(any(CorporateActionEntity.class));
    verify(dividendDetailRepository).save(any(DividendActionDetailEntity.class));
    ArgumentCaptor<DividendDetectedEvent> eventCaptor =
        ArgumentCaptor.forClass(DividendDetectedEvent.class);
    verify(eventPublisher).publishEvent(eventCaptor.capture());
    DividendDetectedEvent published = eventCaptor.getValue();
    assertThat(published.assetSymbol()).isEqualTo("GOOGL");
    assertThat(published.exDividendDate()).isEqualTo(ex);
    assertThat(published.paymentDate()).isEqualTo(pay);
    assertThat(published.dividendPerShare()).isEqualByComparingTo("0.21");
    assertThat(published.currency()).isEqualTo("USD");
  }

  @Test
  void noReescribeEventoCongeladoAunqueElMontoCambie() {
    LocalDate ex = LocalDate.now(ZoneOffset.UTC).minusMonths(2);
    LocalDate pay = ex.plusDays(7);
    CorporateActionEntity frozen = stored(ex, pay, CorporateActionStatus.COMPLETED, "e1");
    when(dividendProvider.fetchDividends("GOOGL"))
        .thenReturn(List.of(event(ex, pay, "567.97", "e1")));
    when(corporateActionRepository.findByProviderEventId("e1")).thenReturn(Optional.of(frozen));

    int created = service.sync("GOOGL");

    assertThat(created).isZero();
    verify(corporateActionRepository, never()).save(any());
    verify(dividendDetailRepository, never()).save(any());
    verifyNoInteractions(eventPublisher);
  }

  @Test
  void actualizaEventoProvisionalRevisado() {
    LocalDate ex = LocalDate.now(ZoneOffset.UTC).plusMonths(2);
    LocalDate pay = ex.plusDays(7);
    CorporateActionEntity provisional = stored(ex, pay, CorporateActionStatus.ANNOUNCED, "e1");
    DividendActionDetailEntity detail = detail(provisional, "0.20");
    when(dividendProvider.fetchDividends("GOOGL"))
        .thenReturn(List.of(event(ex, pay, "0.25", "e1")));
    when(corporateActionRepository.findByProviderEventId("e1")).thenReturn(Optional.of(provisional));
    when(dividendDetailRepository.findById(provisional.getId())).thenReturn(Optional.of(detail));

    int created = service.sync("GOOGL");

    assertThat(created).isZero();
    ArgumentCaptor<DividendActionDetailEntity> savedDetail =
        ArgumentCaptor.forClass(DividendActionDetailEntity.class);
    verify(corporateActionRepository).save(provisional);
    verify(dividendDetailRepository).save(savedDetail.capture());
    assertThat(savedDetail.getValue().getAmountPerShare()).isEqualByComparingTo("0.25");
    verifyNoInteractions(eventPublisher);
  }
}
