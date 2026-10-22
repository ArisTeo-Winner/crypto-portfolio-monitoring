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

import com.mx.cryptomonitor.asset.application.port.in.SecuritySuccessionPort;
import com.mx.cryptomonitor.marketdata.application.port.out.MarketZoneQueryPort;
import com.mx.cryptomonitor.portfolio.application.port.in.SecurityPositionPort;
import com.mx.cryptomonitor.portfolio.application.port.in.SecurityPositionPort.SecurityPosition;
import com.mx.cryptomonitor.transaction.application.dto.request.BuyTransactionRequest;
import com.mx.cryptomonitor.transaction.application.dto.request.SellTransactionRequest;
import com.mx.cryptomonitor.transaction.application.dto.request.TransferTransactionRequest;
import com.mx.cryptomonitor.transaction.application.port.in.MergerAccrualUseCase;
import com.mx.cryptomonitor.transaction.application.port.in.TransactionCommandUseCase;
import com.mx.cryptomonitor.transaction.domain.merger.MergerEffect;
import com.mx.cryptomonitor.transaction.domain.merger.MergerEffectCalculator;
import com.mx.cryptomonitor.transaction.domain.model.Transaction;
import com.mx.cryptomonitor.transaction.domain.repository.TransactionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Accrual de fusiones por usuario (ADR-0014 F6b-2). Para cada tenedor del predecesor, aplica el
 * efecto reusando TRANSFER + BUY / SELL (sin tipos nuevos):
 *
 * <ul>
 *   <li>ALL_STOCK: TRANSFER_OUT del predecesor (baja basis-neutral) + BUY del sucesor al costo base
 *       cargado (carryover, {@code precio = costoBase/qty}).
 *   <li>ALL_CASH: SELL del predecesor a {@code cashPerShare} (el motor realiza el PnL).
 * </ul>
 *
 * Idempotente por claves deterministas; al final marca la sucesión en el catálogo (F6a). El costo
 * base por usuario viene de portfolio ({@link SecurityPositionPort}, fuente de verdad del ACB).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MergerAccrualService implements MergerAccrualUseCase {

  private static final String ASSET_TYPE = "STOCK";
  private static final String FALLBACK_ZONE = "America/New_York";
  private static final int UNIT_SCALE = 8;

  private final TransactionRepository transactionRepository;
  private final SecurityPositionPort securityPositionPort;
  private final TransactionCommandUseCase transactionCommand;
  private final SecuritySuccessionPort securitySuccessionPort;
  private final MarketZoneQueryPort marketZoneQuery;
  private final MergerEffectCalculator mergerCalculator = new MergerEffectCalculator();

  @Override
  public int accrue(
      String predecessorSymbol,
      String successorSymbol,
      LocalDate effectiveDate,
      BigDecimal exchangeRatio,
      BigDecimal cashPerShare,
      String currency) {
    if (!StringUtils.hasText(predecessorSymbol) || effectiveDate == null) {
      return 0;
    }
    String predecessor = predecessorSymbol.trim().toUpperCase();
    String successor =
        StringUtils.hasText(successorSymbol) ? successorSymbol.trim().toUpperCase() : null;
    OffsetDateTime operationDate = resolveOperationDate(effectiveDate, currency);

    int created = 0;
    for (UUID userId : holdersOf(predecessor)) {
      SecurityPosition position = securityPositionPort.positionOf(userId, predecessor).orElse(null);
      if (position == null || position.quantity() == null || position.quantity().signum() <= 0) {
        continue;
      }
      try {
        applyToHolder(
            userId,
            predecessor,
            successor,
            effectiveDate,
            operationDate,
            currency,
            exchangeRatio,
            cashPerShare,
            position);
        created++;
      } catch (RuntimeException ex) {
        log.error(
            "Accrual de fusión falló para user={} {}→{}: {}",
            userId,
            predecessor,
            successor,
            ex.getMessage());
      }
    }

    if (created > 0) {
      securitySuccessionPort.recordMerger(predecessor, successor);
      log.info(
          "Accrual fusión {}→{} efectiva {}: {} usuario(s)",
          predecessor,
          successor,
          effectiveDate,
          created);
    }
    return created;
  }

  private void applyToHolder(
      UUID userId,
      String predecessor,
      String successor,
      LocalDate effectiveDate,
      OffsetDateTime operationDate,
      String currency,
      BigDecimal exchangeRatio,
      BigDecimal cashPerShare,
      SecurityPosition position) {
    MergerEffect effect =
        mergerCalculator.compute(
            position.quantity(), position.costBasis(), exchangeRatio, cashPerShare);

    if (effect.successorQuantity().signum() > 0) {
      // ALL_STOCK: baja basis-neutral del predecesor + alta del sucesor con el costo cargado.
      transactionCommand.registerTransferTransaction(
          userId,
          new TransferTransactionRequest(
              predecessor,
              ASSET_TYPE,
              "TRANSFER_OUT",
              position.quantity(),
              BigDecimal.ZERO,
              operationDate,
              "Merger " + predecessor + "->" + successor),
          key("out", predecessor, effectiveDate, userId));

      if (successor != null && effect.successorCostBasis().signum() > 0) {
        BigDecimal pricePerUnit =
            effect
                .successorCostBasis()
                .divide(effect.successorQuantity(), UNIT_SCALE, RoundingMode.HALF_UP);
        transactionCommand.registerBuyTransaction(
            userId,
            new BuyTransactionRequest(
                successor,
                ASSET_TYPE,
                effect.successorQuantity(),
                pricePerUnit,
                BigDecimal.ZERO,
                operationDate,
                "Merger carryover " + predecessor + "->" + successor,
                null,
                null,
                null,
                currency,
                null,
                null,
                null,
                null),
            key("in", successor, effectiveDate, userId));
      }
      return;
    }

    if (effect.cashProceeds().signum() > 0) {
      // ALL_CASH: cierre a efectivo; el motor realiza el PnL contra el costo base.
      transactionCommand.registerSellTransaction(
          userId,
          new SellTransactionRequest(
              predecessor,
              ASSET_TYPE,
              position.quantity(),
              cashPerShare,
              BigDecimal.ZERO,
              operationDate,
              "Merger cash-out " + predecessor,
              null,
              null,
              null,
              currency,
              null,
              null,
              null,
              null),
          key("cash", predecessor, effectiveDate, userId));
    }
  }

  private Set<UUID> holdersOf(String predecessor) {
    Set<UUID> users = new LinkedHashSet<>();
    for (Transaction tx :
        transactionRepository.findByAssetSymbolOrderByTransactionDateAscCreatedAtAsc(predecessor)) {
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

  private String key(String leg, String symbol, LocalDate effectiveDate, UUID userId) {
    return "merger:" + leg + ":" + symbol + ":" + effectiveDate + ":" + userId;
  }
}
