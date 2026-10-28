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
import com.mx.cryptomonitor.transaction.application.port.in.TenderAccrualUseCase;
import com.mx.cryptomonitor.transaction.application.port.in.TransactionCommandUseCase;
import com.mx.cryptomonitor.transaction.domain.model.Transaction;
import com.mx.cryptomonitor.transaction.domain.repository.TransactionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Accrual de ofertas públicas (OPA) por usuario (ADR-0014 F6c-2). Para cada tenedor de la seguridad
 * objeto, cierra la posición completa con un SELL a {@code offerPrice} (el motor realiza el PnL
 * contra el costo base). Asume aceptación total. Si la OPA derivó en baja, marca la seguridad
 * DELISTED (F6a). Idempotente por clave determinista. El costo base vive en portfolio (SELL lo
 * usa).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenderAccrualService implements TenderAccrualUseCase {

  private static final String ASSET_TYPE = "STOCK";
  private static final String FALLBACK_ZONE = "America/New_York";

  private final TransactionRepository transactionRepository;
  private final SecurityPositionPort securityPositionPort;
  private final TransactionCommandUseCase transactionCommand;
  private final SecurityStatusPort securityStatusPort;
  private final MarketZoneQueryPort marketZoneQuery;

  @Override
  public int accrue(
      String targetSymbol,
      BigDecimal offerPrice,
      String currency,
      LocalDate offerDate,
      boolean resultedInDelisting) {
    if (!StringUtils.hasText(targetSymbol)
        || offerPrice == null
        || offerPrice.signum() <= 0
        || offerDate == null) {
      return 0;
    }
    String symbol = targetSymbol.trim().toUpperCase();
    OffsetDateTime operationDate = resolveOperationDate(offerDate, currency);

    int created = 0;
    for (UUID userId : holdersOf(symbol)) {
      SecurityPosition position = securityPositionPort.positionOf(userId, symbol).orElse(null);
      if (position == null || position.quantity() == null || position.quantity().signum() <= 0) {
        continue;
      }
      try {
        transactionCommand.registerSellTransaction(
            userId,
            new SellTransactionRequest(
                symbol,
                ASSET_TYPE,
                position.quantity(),
                offerPrice,
                BigDecimal.ZERO,
                operationDate,
                "Tender offer cash-out " + symbol,
                null,
                null,
                null,
                currency,
                null,
                null,
                null,
                null),
            "tender:" + symbol + ":" + offerDate + ":" + userId);
        created++;
      } catch (RuntimeException ex) {
        log.error(
            "Accrual de OPA falló para user={} symbol={}: {}", userId, symbol, ex.getMessage());
      }
    }

    if (created > 0 && resultedInDelisting) {
      securityStatusPort.markDelisted(symbol);
    }
    if (created > 0) {
      log.info("Accrual OPA {} oferta {}: {} usuario(s)", symbol, offerDate, created);
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

  private OffsetDateTime resolveOperationDate(LocalDate offerDate, String currency) {
    String zoneId = marketZoneQuery.zoneIdFor(currency, null).orElse(FALLBACK_ZONE);
    return offerDate.atStartOfDay(ZoneId.of(zoneId)).toOffsetDateTime();
  }
}
