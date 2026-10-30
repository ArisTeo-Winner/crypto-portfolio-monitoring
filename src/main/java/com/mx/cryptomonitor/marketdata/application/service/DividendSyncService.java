package com.mx.cryptomonitor.marketdata.application.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.mx.cryptomonitor.marketdata.application.port.out.DividendDetectedEvent;
import com.mx.cryptomonitor.marketdata.application.port.out.DividendEventData;
import com.mx.cryptomonitor.marketdata.application.port.out.DividendProviderPort;
import com.mx.cryptomonitor.marketdata.domain.corporateaction.CorporateActionLifecycle;
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
 * Ingesta de dividendos (ADR-0014 F3b + ADR-0020): trae el historico del proveedor y lo persiste
 * como eventos DIVIDEND en {@code corporate_action} + {@code dividend_action_detail}. Idempotente:
 * dedup por {@code provider_event_id} (fuerte) y por la llave natural (simbolo+tipo+ex-date).
 *
 * <p>ADR-0020 (freeze-al-pago): un evento ya {@code COMPLETED} (pagado) es INMUTABLE — nunca se
 * re-escriben sus montos ni fechas; a lo sumo se sincroniza la etiqueta de estado. Un evento
 * provisional ({@code ANNOUNCED}/{@code EFFECTIVE}) SI se actualiza si el emisor lo revisa. El
 * estado se deriva de las fechas, no se congela al sync-time. Solo DETECTA (puebla el catalogo); el
 * accrual por usuario y la cadena fiscal son F3c/F4.
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
    LocalDate today = LocalDate.now(ZoneOffset.UTC);
    int created = 0;
    int updated = 0;
    for (DividendEventData event : events) {
      if (event.exDividendDate() == null) {
        continue;
      }
      Optional<CorporateActionEntity> existing = findExisting(symbol, event);
      if (existing.isPresent()) {
        if (reconcileExisting(existing.get(), event, today)) {
          updated++;
        }
        continue;
      }
      CorporateActionEntity action =
          corporateActionRepository.save(toCorporateAction(symbol, event, today));
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
    if (created > 0 || updated > 0) {
      log.info(
          "Dividend sync {}: {} evento(s) nuevo(s), {} actualizado(s)", symbol, created, updated);
    }
    return created;
  }

  private Optional<CorporateActionEntity> findExisting(String symbol, DividendEventData event) {
    if (StringUtils.hasText(event.providerEventId())) {
      Optional<CorporateActionEntity> byProvider =
          corporateActionRepository.findByProviderEventId(event.providerEventId());
      if (byProvider.isPresent()) {
        return byProvider;
      }
    }
    return corporateActionRepository.findByAssetSymbolAndActionTypeAndPivotalDate(
        symbol, CorporateActionType.DIVIDEND, event.exDividendDate());
  }

  /**
   * Reconcilia un evento ya almacenado (ADR-0020). Congelado (pagado) => inmutable: no se tocan
   * montos ni fechas, solo se sincroniza la etiqueta de estado si quedo vieja. Provisional => se
   * actualiza si el emisor reviso monto o fechas. Devuelve {@code true} si hubo cambios de datos.
   */
  private boolean reconcileExisting(
      CorporateActionEntity stored, DividendEventData event, LocalDate today) {
    CorporateActionStatus storedDerived =
        CorporateActionLifecycle.statusFor(stored.getPivotalDate(), stored.getPaymentDate(), today);

    if (CorporateActionLifecycle.isFrozen(storedDerived)) {
      if (stored.getStatus() != storedDerived) {
        stored.setStatus(storedDerived);
        stored.setSyncedAt(OffsetDateTime.now(ZoneOffset.UTC));
        corporateActionRepository.save(stored);
      }
      return false;
    }

    return applyProvisionalChanges(stored, event, today);
  }

  private boolean applyProvisionalChanges(
      CorporateActionEntity stored, DividendEventData event, LocalDate today) {
    boolean changed = false;

    if (!Objects.equals(stored.getPivotalDate(), event.exDividendDate())) {
      stored.setPivotalDate(event.exDividendDate());
      changed = true;
    }
    if (!Objects.equals(stored.getDeclarationDate(), event.declarationDate())) {
      stored.setDeclarationDate(event.declarationDate());
      changed = true;
    }
    if (!Objects.equals(stored.getRecordDate(), event.recordDate())) {
      stored.setRecordDate(event.recordDate());
      changed = true;
    }
    if (!Objects.equals(stored.getPaymentDate(), event.paymentDate())) {
      stored.setPaymentDate(event.paymentDate());
      changed = true;
    }

    CorporateActionStatus newStatus =
        CorporateActionLifecycle.statusFor(event.exDividendDate(), event.paymentDate(), today);
    if (stored.getStatus() != newStatus) {
      stored.setStatus(newStatus);
      changed = true;
    }

    DividendActionDetailEntity detail =
        dividendDetailRepository.findById(stored.getId()).orElse(null);
    if (detail != null) {
      String currency = event.currency() == null ? "USD" : event.currency();
      DividendDistributionType distributionType = resolveDistributionType(event.distributionType());
      if (!amountsEqual(detail.getAmountPerShare(), event.cashAmount())) {
        detail.setAmountPerShare(event.cashAmount());
        changed = true;
      }
      if (!Objects.equals(detail.getCurrency(), currency)) {
        detail.setCurrency(currency);
        changed = true;
      }
      if (!Objects.equals(detail.getFrequency(), event.frequency())) {
        detail.setFrequency(event.frequency());
        changed = true;
      }
      if (detail.getDistributionType() != distributionType) {
        detail.setDistributionType(distributionType);
        changed = true;
      }
      if (!amountsEqual(detail.getSplitAdjustedDps(), event.splitAdjustedCashAmount())) {
        detail.setSplitAdjustedDps(event.splitAdjustedCashAmount());
        changed = true;
      }
      if (!amountsEqual(detail.getPriceAdjustmentFactor(), event.historicalAdjustmentFactor())) {
        detail.setPriceAdjustmentFactor(event.historicalAdjustmentFactor());
        changed = true;
      }
    }

    if (changed) {
      stored.setSyncedAt(OffsetDateTime.now(ZoneOffset.UTC));
      corporateActionRepository.save(stored);
      if (detail != null) {
        dividendDetailRepository.save(detail);
      }
    }
    return changed;
  }

  private static boolean amountsEqual(BigDecimal a, BigDecimal b) {
    if (a == null || b == null) {
      return a == b;
    }
    return a.compareTo(b) == 0;
  }

  private CorporateActionEntity toCorporateAction(
      String symbol, DividendEventData event, LocalDate today) {
    return CorporateActionEntity.builder()
        .assetSymbol(symbol)
        .actionType(CorporateActionType.DIVIDEND)
        .status(
            CorporateActionLifecycle.statusFor(event.exDividendDate(), event.paymentDate(), today))
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

  private DividendDistributionType resolveDistributionType(String raw) {
    return "special".equalsIgnoreCase(raw)
        ? DividendDistributionType.SPECIAL
        : DividendDistributionType.RECURRING;
  }
}
