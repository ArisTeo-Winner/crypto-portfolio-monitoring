package com.mx.cryptomonitor.marketdata.application.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.mx.cryptomonitor.marketdata.application.port.out.DividendDetectedEvent;
import com.mx.cryptomonitor.marketdata.application.port.out.DividendEventData;
import com.mx.cryptomonitor.marketdata.application.port.out.DividendProviderPort;
import com.mx.cryptomonitor.marketdata.domain.model.CorporateActionEntity;
import com.mx.cryptomonitor.marketdata.domain.model.CorporateActionStatus;
import com.mx.cryptomonitor.marketdata.domain.model.CorporateActionType;
import com.mx.cryptomonitor.marketdata.domain.model.DividendActionDetailEntity;
import com.mx.cryptomonitor.marketdata.domain.model.DividendDistributionType;
import com.mx.cryptomonitor.marketdata.domain.repository.CorporateActionRepository;
import com.mx.cryptomonitor.marketdata.domain.repository.DividendActionDetailRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Ingesta de dividendos (ADR-0014 F3b): trae el historico del proveedor y lo persiste como eventos
 * DIVIDEND en {@code corporate_action} + {@code dividend_action_detail}. Idempotente: dedup por
 * {@code provider_event_id} (fuerte) y por la llave natural (simbolo+tipo+ex-date). Solo DETECTA
 * (puebla el catalogo de eventos); el accrual por usuario y la cadena fiscal son F3c/F4.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DividendSyncService {

  private static final String PROVIDER = "MASSIVE";

  private final DividendProviderPort dividendProvider;
  private final CorporateActionRepository corporateActionRepository;
  private final DividendActionDetailRepository dividendDetailRepository;
  private final ApplicationEventPublisher eventPublisher;

  /** Sincroniza los dividendos de un ticker. Devuelve cuantos eventos NUEVOS se insertaron. */
  @Transactional
  public int sync(String ticker) {
    if (!StringUtils.hasText(ticker)) {
      return 0;
    }
    String symbol = ticker.trim().toUpperCase();
    List<DividendEventData> events = dividendProvider.fetchDividends(symbol);
    int created = 0;
    for (DividendEventData event : events) {
      if (event.exDividendDate() == null || isAlreadyStored(symbol, event)) {
        continue;
      }
      CorporateActionEntity action =
          corporateActionRepository.save(toCorporateAction(symbol, event));
      dividendDetailRepository.save(toDetail(action, event));
      eventPublisher.publishEvent(
          new DividendDetectedEvent(
              symbol,
              event.exDividendDate(),
              event.paymentDate(),
              event.cashAmount(),
              event.currency() == null ? "USD" : event.currency()));
      created++;
    }
    if (created > 0) {
      log.info("Dividend sync {}: {} evento(s) nuevo(s)", symbol, created);
    }
    return created;
  }

  private boolean isAlreadyStored(String symbol, DividendEventData event) {
    if (StringUtils.hasText(event.providerEventId())
        && corporateActionRepository.findByProviderEventId(event.providerEventId()).isPresent()) {
      return true;
    }
    return corporateActionRepository.existsByAssetSymbolAndActionTypeAndPivotalDate(
        symbol, CorporateActionType.DIVIDEND, event.exDividendDate());
  }

  private CorporateActionEntity toCorporateAction(String symbol, DividendEventData event) {
    return CorporateActionEntity.builder()
        .assetSymbol(symbol)
        .actionType(CorporateActionType.DIVIDEND)
        .status(resolveStatus(event))
        .pivotalDate(event.exDividendDate())
        .declarationDate(event.declarationDate())
        .recordDate(event.recordDate())
        .paymentDate(event.paymentDate())
        .sourceProvider(PROVIDER)
        .providerEventId(event.providerEventId())
        .syncedAt(OffsetDateTime.now(ZoneOffset.UTC))
        .build();
  }

  private DividendActionDetailEntity toDetail(
      CorporateActionEntity action, DividendEventData event) {
    return DividendActionDetailEntity.builder()
        .corporateAction(action)
        .amountPerShare(event.cashAmount())
        .currency(event.currency() == null ? "USD" : event.currency())
        .frequency(event.frequency())
        .distributionType(resolveDistributionType(event.distributionType()))
        .splitAdjustedDps(event.splitAdjustedCashAmount())
        .priceAdjustmentFactor(event.historicalAdjustmentFactor())
        .build();
  }

  private CorporateActionStatus resolveStatus(DividendEventData event) {
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    if (event.paymentDate() != null && !event.paymentDate().isAfter(today)) {
      return CorporateActionStatus.COMPLETED;
    }
    if (!event.exDividendDate().isAfter(today)) {
      return CorporateActionStatus.EFFECTIVE;
    }
    return CorporateActionStatus.ANNOUNCED;
  }

  private DividendDistributionType resolveDistributionType(String raw) {
    return "special".equalsIgnoreCase(raw)
        ? DividendDistributionType.SPECIAL
        : DividendDistributionType.RECURRING;
  }
}
