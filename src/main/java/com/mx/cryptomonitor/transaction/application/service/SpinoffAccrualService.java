package com.mx.cryptomonitor.transaction.application.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.mx.cryptomonitor.marketdata.application.port.out.MarketZoneQueryPort;
import com.mx.cryptomonitor.portfolio.application.port.in.SecurityPositionPort;
import com.mx.cryptomonitor.portfolio.application.port.in.SecurityPositionPort.SecurityPosition;
import com.mx.cryptomonitor.transaction.application.dto.request.BuyTransactionRequest;
import com.mx.cryptomonitor.transaction.application.dto.request.TransferTransactionRequest;
import com.mx.cryptomonitor.transaction.application.port.in.SpinoffAccrualUseCase;
import com.mx.cryptomonitor.transaction.application.port.in.TransactionCommandUseCase;
import com.mx.cryptomonitor.transaction.domain.model.Transaction;
import com.mx.cryptomonitor.transaction.domain.repository.TransactionRepository;
import com.mx.cryptomonitor.transaction.domain.spinoff.SpinoffEffect;
import com.mx.cryptomonitor.transaction.domain.spinoff.SpinoffEffectCalculator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Accrual de spinoffs por usuario (ADR-0014 F6e-2). Reparte el costo base sin realizar PnL,
 * reusando tipos existentes (path interno; el {@code @Positive} vive solo en el controller):
 *
 * <ol>
 *   <li>TRANSFER_OUT de la matriz (baja basis-neutral);
 *   <li>BUY de la matriz por la MISMA cantidad al precio {@code baseRetenida/qty} (re-base);
 *   <li>BUY de la spinco por {@code qty*ratio} al precio {@code baseAsignada/qtySpinco}.
 * </ol>
 *
 * Idempotente por clave determinista. Costo base desde portfolio (ACB, fuente de verdad).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SpinoffAccrualService implements SpinoffAccrualUseCase {

  private static final String ASSET_TYPE = "STOCK";
  private static final String FALLBACK_ZONE = "America/New_York";
  private static final int UNIT_SCALE = 8;

  private final TransactionRepository transactionRepository;
  private final SecurityPositionPort securityPositionPort;
  private final TransactionCommandUseCase transactionCommand;
  private final MarketZoneQueryPort marketZoneQuery;
  private final SpinoffEffectCalculator spinoffCalculator = new SpinoffEffectCalculator();

  @Override
  public int accrue(
      String parentSymbol,
      String spincoSymbol,
      BigDecimal sharesPerParentShare,
      BigDecimal costBasisAllocationPct,
      String currency,
      LocalDate effectiveDate) {
    if (!StringUtils.hasText(parentSymbol)
        || !StringUtils.hasText(spincoSymbol)
        || effectiveDate == null) {
      return 0;
    }
    String parent = parentSymbol.trim().toUpperCase();
    String spinco = spincoSymbol.trim().toUpperCase();
    OffsetDateTime operationDate = resolveOperationDate(effectiveDate, currency);

    int created = 0;
    for (UUID userId : holdersOf(parent)) {
      SecurityPosition position = securityPositionPort.positionOf(userId, parent).orElse(null);
      if (position == null || position.quantity() == null || position.quantity().signum() <= 0) {
        continue;
      }
      try {
        applyToHolder(
            userId,
            parent,
            spinco,
            sharesPerParentShare,
            costBasisAllocationPct,
            currency,
            effectiveDate,
            operationDate,
            position);
        created++;
      } catch (RuntimeException ex) {
        log.error(
            "Accrual de spinoff falló para user={} {}→{}: {}",
            userId,
            parent,
            spinco,
            ex.getMessage());
      }
    }
    if (created > 0) {
      log.info(
          "Accrual spinoff {}→{} efectivo {}: {} usuario(s)",
          parent,
          spinco,
          effectiveDate,
          created);
    }
    return created;
  }

  private void applyToHolder(
      UUID userId,
      String parent,
      String spinco,
      BigDecimal sharesPerParentShare,
      BigDecimal allocationPct,
      String currency,
      LocalDate effectiveDate,
      OffsetDateTime operationDate,
      SecurityPosition position) {
    SpinoffEffect effect =
        spinoffCalculator.compute(
            position.quantity(), position.costBasis(), sharesPerParentShare, allocationPct);

    // Re-base de la matriz: baja basis-neutral + alta de la MISMA cantidad al costo retenido.
    transactionCommand.registerTransferTransaction(
        userId,
        new TransferTransactionRequest(
            parent,
            ASSET_TYPE,
            "TRANSFER_OUT",
            position.quantity(),
            BigDecimal.ZERO,
            operationDate,
            "Spinoff re-base " + parent + "->" + spinco),
        "spinoff:out:" + parent + ":" + effectiveDate + ":" + userId);

    transactionCommand.registerBuyTransaction(
        userId,
        buy(
            parent,
            position.quantity(),
            unitPrice(effect.retainedParentBasis(), position.quantity()),
            currency,
            operationDate,
            "Spinoff retained basis " + parent),
        "spinoff:rebase:" + parent + ":" + effectiveDate + ":" + userId);

    // Entrega de la spinco con la base asignada.
    if (effect.spincoQuantity().signum() > 0) {
      transactionCommand.registerBuyTransaction(
          userId,
          buy(
              spinco,
              effect.spincoQuantity(),
              unitPrice(effect.spincoBasis(), effect.spincoQuantity()),
              currency,
              operationDate,
              "Spinoff distribution " + parent + "->" + spinco),
          "spinoff:in:" + spinco + ":" + effectiveDate + ":" + userId);
    }
  }

  private BigDecimal unitPrice(BigDecimal basis, BigDecimal quantity) {
    if (quantity == null || quantity.signum() == 0) {
      return BigDecimal.ZERO;
    }
    return basis.divide(quantity, UNIT_SCALE, RoundingMode.HALF_UP);
  }

  private BuyTransactionRequest buy(
      String symbol,
      BigDecimal quantity,
      BigDecimal pricePerUnit,
      String currency,
      OffsetDateTime operationDate,
      String notes) {
    return new BuyTransactionRequest(
        symbol,
        ASSET_TYPE,
        quantity,
        pricePerUnit,
        BigDecimal.ZERO,
        operationDate,
        notes,
        null,
        null,
        null,
        currency,
        null,
        null,
        null,
        null);
  }

  private Set<UUID> holdersOf(String symbol) {
    Set<UUID> users = new LinkedHashSet<>();
    for (Transaction tx :
        transactionRepository.findByAssetSymbolOrderByTransactionDateAscCreatedAtAsc(symbol)) {
      if (tx.getUser() != null && tx.getUser().getId() != null) {
        users.add(tx.getUser().getId());
      }
    }
    return users;
  }

  private OffsetDateTime resolveOperationDate(LocalDate effectiveDate, String currency) {
    String zoneId = marketZoneQuery.zoneIdFor(currency, null).orElse(FALLBACK_ZONE);
    return effectiveDate.atStartOfDay(ZoneId.of(zoneId)).toOffsetDateTime();
  }
}
