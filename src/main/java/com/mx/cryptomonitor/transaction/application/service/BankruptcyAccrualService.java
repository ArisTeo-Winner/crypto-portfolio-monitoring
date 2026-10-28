package com.mx.cryptomonitor.transaction.application.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.mx.cryptomonitor.asset.application.port.in.SecurityStatusPort;
import com.mx.cryptomonitor.marketdata.application.port.out.MarketZoneQueryPort;
import com.mx.cryptomonitor.portfolio.application.port.in.SecurityPositionPort;
import com.mx.cryptomonitor.portfolio.application.port.in.SecurityPositionPort.SecurityPosition;
import com.mx.cryptomonitor.transaction.application.dto.request.SellTransactionRequest;
import com.mx.cryptomonitor.transaction.application.port.in.BankruptcyAccrualUseCase;
import com.mx.cryptomonitor.transaction.application.port.in.TransactionCommandUseCase;
import com.mx.cryptomonitor.transaction.domain.model.Transaction;
import com.mx.cryptomonitor.transaction.domain.repository.TransactionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Accrual de quiebras por usuario (ADR-0014 F6d-2). Para cada tenedor, cierra la posición completa
 * con un SELL a {@code recoveryPerShare} (el motor realiza la pérdida contra el costo base; {@code
 * 0} = write-off total). Es un path INTERNO: va directo al servicio, no por el endpoint público, de
 * modo que el {@code @Positive} de la petición (que vive en el controller) no bloquea el precio 0.
 * Al final marca la seguridad BANKRUPT (F6a). Idempotente por clave determinista.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BankruptcyAccrualService implements BankruptcyAccrualUseCase {

  private static final String ASSET_TYPE = "STOCK";
  private static final String FALLBACK_ZONE = "America/New_York";

  private final TransactionRepository transactionRepository;
  private final SecurityPositionPort securityPositionPort;
  private final TransactionCommandUseCase transactionCommand;
  private final SecurityStatusPort securityStatusPort;
  private final MarketZoneQueryPort marketZoneQuery;

  @Override
  public int accrue(
      String symbol, BigDecimal recoveryPerShare, String currency, LocalDate effectiveDate) {
    if (!StringUtils.hasText(symbol)
        || recoveryPerShare == null
        || recoveryPerShare.signum() < 0
        || effectiveDate == null) {
      return 0;
    }
    String normalized = symbol.trim().toUpperCase();
    OffsetDateTime operationDate = resolveOperationDate(effectiveDate, currency);

    int created = 0;
    for (UUID userId : holdersOf(normalized)) {
      SecurityPosition position = securityPositionPort.positionOf(userId, normalized).orElse(null);
      if (position == null || position.quantity() == null || position.quantity().signum() <= 0) {
        continue;
      }
      try {
        transactionCommand.registerSellTransaction(
            userId,
            new SellTransactionRequest(
                normalized,
                ASSET_TYPE,
                position.quantity(),
                recoveryPerShare, // 0 permitido: write-off total (path interno, no el endpoint)
                BigDecimal.ZERO,
                operationDate,
                "Bankruptcy write-off " + normalized,
                null,
                null,
                null,
                currency,
                null,
                null,
                null,
                null),
            "bankruptcy:" + normalized + ":" + effectiveDate + ":" + userId);
        created++;
      } catch (RuntimeException ex) {
        log.error(
            "Accrual de quiebra falló para user={} symbol={}: {}",
            userId,
            normalized,
            ex.getMessage());
      }
    }

    if (created > 0) {
      securityStatusPort.markBankrupt(normalized);
      log.info("Accrual quiebra {} efectiva {}: {} usuario(s)", normalized, effectiveDate, created);
    }
    return created;
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
