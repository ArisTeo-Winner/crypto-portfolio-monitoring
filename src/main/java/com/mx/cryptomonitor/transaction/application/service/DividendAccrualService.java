package com.mx.cryptomonitor.transaction.application.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.mx.cryptomonitor.marketdata.application.port.out.MarketZoneQueryPort;
import com.mx.cryptomonitor.transaction.application.dto.request.DividendTransactionRequest;
import com.mx.cryptomonitor.transaction.application.port.in.DividendAccrualUseCase;
import com.mx.cryptomonitor.transaction.application.port.in.TransactionCommandUseCase;
import com.mx.cryptomonitor.transaction.domain.dividend.DividendEntitlementCalculator;
import com.mx.cryptomonitor.transaction.domain.dividend.DividendTaxBreakdown;
import com.mx.cryptomonitor.transaction.domain.dividend.DividendTaxCalculator;
import com.mx.cryptomonitor.transaction.domain.model.Transaction;
import com.mx.cryptomonitor.transaction.domain.repository.DividendDetailRepository;
import com.mx.cryptomonitor.transaction.domain.repository.TransactionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Accrual de dividendos por usuario (ADR-0014 F3c): a partir de un evento DIVIDEND detectado
 * (simbolo, ex-date, payment date, DPS), crea una transaccion DIVIDEND por cada usuario con
 * derecho.
 *
 * <ol>
 *   <li>entitlement: tenencia neta con fecha de operacion {@code <} ex-date ({@link
 *       DividendEntitlementCalculator});
 *   <li>cadena fiscal US-&gt;MX sobre el bruto cantidad x DPS ({@link DividendTaxCalculator});
 *   <li>alta reutilizando {@code registerDividendTransaction} (portfolio, auditoria y binomio
 *       ADR-0013 ya resueltos), con {@code idempotencyKey} determinista.
 * </ol>
 *
 * <p>Idempotente: un dividendo por {@code (usuario, simbolo, ex-date)}; un fallo por usuario se
 * registra y no bloquea a los demas.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DividendAccrualService implements DividendAccrualUseCase {

  private static final String FALLBACK_ZONE = "America/New_York";

  private final TransactionRepository transactionRepository;
  private final DividendDetailRepository dividendDetailRepository;
  private final DividendEntitlementCalculator entitlementCalculator;
  private final DividendTaxCalculator taxCalculator;
  private final TransactionCommandUseCase transactionCommand;
  private final MarketZoneQueryPort marketZoneQuery;

  @Override
  public int accrue(
      String assetSymbol,
      String assetType,
      LocalDate exDividendDate,
      LocalDate paymentDate,
      BigDecimal dividendPerShare,
      String currency,
      String exchange) {
    if (assetSymbol == null
        || exDividendDate == null
        || dividendPerShare == null
        || dividendPerShare.signum() <= 0) {
      return 0;
    }
    String symbol = assetSymbol.trim().toUpperCase();
    List<Transaction> history =
        transactionRepository.findByAssetSymbolOrderByTransactionDateAscCreatedAtAsc(symbol);
    Map<UUID, BigDecimal> entitled =
        entitlementCalculator.entitledQuantityByUser(history, exDividendDate);
    if (entitled.isEmpty()) {
      return 0;
    }

    OffsetDateTime operationDate =
        resolveOperationDate(paymentDate, exDividendDate, currency, exchange);
    int created = 0;
    for (Map.Entry<UUID, BigDecimal> holder : entitled.entrySet()) {
      UUID userId = holder.getKey();
      if (dividendDetailRepository
          .existsByTransaction_User_IdAndTransaction_AssetSymbolAndExDividendDate(
              userId, symbol, exDividendDate)) {
        continue;
      }
      try {
        DividendTaxBreakdown tax =
            taxCalculator.foreignCashDividend(holder.getValue(), dividendPerShare);
        DividendTransactionRequest request =
            new DividendTransactionRequest(
                symbol,
                null,
                assetType,
                tax.grossAmount(),
                operationDate,
                exDividendDate,
                "CASH",
                tax.totalWithheld(),
                null,
                currency,
                exchange);
        transactionCommand.registerDividendTransaction(
            userId, request, idempotencyKey(symbol, exDividendDate, userId));
        created++;
      } catch (RuntimeException ex) {
        log.error(
            "Accrual de dividendo fallo para user={} symbol={} exDate={}: {}",
            userId,
            symbol,
            exDividendDate,
            ex.getMessage());
      }
    }
    if (created > 0) {
      log.info("Accrual dividendo {} ex={}: {} usuario(s)", symbol, exDividendDate, created);
    }
    return created;
  }

  private OffsetDateTime resolveOperationDate(
      LocalDate paymentDate, LocalDate exDividendDate, String currency, String exchange) {
    LocalDate eventDate = paymentDate != null ? paymentDate : exDividendDate;
    String zoneId = marketZoneQuery.zoneIdFor(currency, exchange).orElse(FALLBACK_ZONE);
    return eventDate.atStartOfDay(ZoneId.of(zoneId)).toOffsetDateTime();
  }

  private String idempotencyKey(String symbol, LocalDate exDividendDate, UUID userId) {
    return "accrual:dividend:" + symbol + ":" + exDividendDate + ":" + userId;
  }
}
