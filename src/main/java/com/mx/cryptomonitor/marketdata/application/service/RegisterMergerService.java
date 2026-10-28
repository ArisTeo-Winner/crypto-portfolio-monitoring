package com.mx.cryptomonitor.marketdata.application.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.mx.cryptomonitor.marketdata.application.port.in.RegisterMergerUseCase;
import com.mx.cryptomonitor.marketdata.application.port.out.MergerDetectedEvent;
import com.mx.cryptomonitor.marketdata.domain.model.CorporateActionEntity;
import com.mx.cryptomonitor.marketdata.domain.model.CorporateActionStatus;
import com.mx.cryptomonitor.marketdata.domain.model.CorporateActionType;
import com.mx.cryptomonitor.marketdata.domain.model.MergerActionDetailEntity;
import com.mx.cryptomonitor.marketdata.domain.model.MergerType;
import com.mx.cryptomonitor.marketdata.domain.repository.CorporateActionRepository;
import com.mx.cryptomonitor.marketdata.domain.repository.MergerActionDetailRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Registro administrado de fusiones (ADR-0015). Persiste el evento MERGER + detalle de forma
 * idempotente (llave natural símbolo+tipo+fecha efectiva) y publica {@link MergerDetectedEvent}
 * solo cuando es NUEVO; el accrual (transaction) corre en un listener AFTER_COMMIT. No depende de
 * transaction.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RegisterMergerService implements RegisterMergerUseCase {

  private static final String PROVIDER = "ADMIN";

  private final CorporateActionRepository corporateActionRepository;
  private final MergerActionDetailRepository mergerDetailRepository;
  private final ApplicationEventPublisher eventPublisher;

  @Override
  @Transactional
  public UUID registerMerger(
      String predecessorSymbol,
      String successorSymbol,
      MergerType mergerType,
      BigDecimal exchangeRatio,
      BigDecimal cashPerShare,
      String currency,
      LocalDate effectiveDate) {
    String predecessor = predecessorSymbol.trim().toUpperCase();
    String successor =
        StringUtils.hasText(successorSymbol) ? successorSymbol.trim().toUpperCase() : null;
    String providerEventId = "merger:" + predecessor + ":" + effectiveDate;

    return corporateActionRepository
        .findByProviderEventId(providerEventId)
        .map(CorporateActionEntity::getId) // ya registrada: idempotente, sin re-publicar
        .orElseGet(
            () ->
                persistAndPublish(
                    predecessor,
                    successor,
                    mergerType,
                    exchangeRatio,
                    cashPerShare,
                    currency,
                    effectiveDate,
                    providerEventId));
  }

  private UUID persistAndPublish(
      String predecessor,
      String successor,
      MergerType mergerType,
      BigDecimal exchangeRatio,
      BigDecimal cashPerShare,
      String currency,
      LocalDate effectiveDate,
      String providerEventId) {
    CorporateActionStatus status =
        effectiveDate.isAfter(LocalDate.now(ZoneOffset.UTC))
            ? CorporateActionStatus.ANNOUNCED
            : CorporateActionStatus.COMPLETED;
    CorporateActionEntity action =
        corporateActionRepository.save(
            CorporateActionEntity.builder()
                .assetSymbol(predecessor)
                .actionType(CorporateActionType.MERGER)
                .status(status)
                .pivotalDate(effectiveDate)
                .sourceProvider(PROVIDER)
                .providerEventId(providerEventId)
                .syncedAt(OffsetDateTime.now(ZoneOffset.UTC))
                .build());

    mergerDetailRepository.save(
        MergerActionDetailEntity.builder()
            .corporateAction(action)
            .mergerType(mergerType)
            .successorSymbol(successor)
            .exchangeRatio(exchangeRatio)
            .cashPerShare(cashPerShare)
            .currency(currency)
            .build());

    eventPublisher.publishEvent(
        new MergerDetectedEvent(
            predecessor, successor, effectiveDate, exchangeRatio, cashPerShare, currency));
    log.info(
        "Fusión registrada {}→{} efectiva {} ({})",
        predecessor,
        successor,
        effectiveDate,
        mergerType);
    return action.getId();
  }
}
