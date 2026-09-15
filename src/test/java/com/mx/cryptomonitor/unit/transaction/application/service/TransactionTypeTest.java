package com.mx.cryptomonitor.unit.transaction.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mx.cryptomonitor.asset.application.port.in.AssetCatalogQueryPort;
import com.mx.cryptomonitor.asset.application.port.in.AssetCatalogRefreshPort;
import com.mx.cryptomonitor.transaction.application.dto.response.TransactionDetailsResponse;
import com.mx.cryptomonitor.transaction.application.mapper.TransactionMapper;
import com.mx.cryptomonitor.transaction.application.port.out.PortfolioProjectionSyncPort;
import com.mx.cryptomonitor.transaction.application.port.out.TransactionAuditPort;
import com.mx.cryptomonitor.transaction.application.port.out.TransactionRegistrationPort;
import com.mx.cryptomonitor.transaction.application.service.TransactionIdempotencyService;
import com.mx.cryptomonitor.transaction.application.service.TransactionRealizedPnlService;
import com.mx.cryptomonitor.transaction.application.service.TransactionService;
import com.mx.cryptomonitor.transaction.domain.model.AssetType;
import com.mx.cryptomonitor.transaction.domain.model.Transaction;
import com.mx.cryptomonitor.transaction.domain.repository.DividendDetailRepository;
import com.mx.cryptomonitor.transaction.domain.repository.TransactionRepository;

/**
 * TEST 5 — verifica que DIVIDEND y REDEEM son tipos de transacción válidos manejados por el
 * servicio (amountLabel correcto, netAmount calculado).
 */
@ExtendWith(MockitoExtension.class)
class TransactionTypeTest {

  @Mock private TransactionRepository transactionRepository;
  @Mock private TransactionRegistrationPort transactionRegistrationPort;
  @Mock private PortfolioProjectionSyncPort portfolioProjectionSyncPort;
  @Mock private TransactionMapper transactionMapper;
  @Mock private TransactionIdempotencyService transactionIdempotencyService;
  @Mock private TransactionAuditPort transactionAuditPort;
  @Mock private TransactionRealizedPnlService transactionRealizedPnlService;
  @Mock private DividendDetailRepository dividendDetailRepository;
  @Mock private AssetCatalogQueryPort assetCatalogQueryPort;
  @Mock private AssetCatalogRefreshPort assetCatalogRefreshPort;

  @InjectMocks private TransactionService service;

  private Transaction buildTransaction(String transactionType, BigDecimal totalValue) {
    return Transaction.builder()
        .transactionId(UUID.randomUUID())
        .assetSymbol("AAPL")
        .assetType(AssetType.STOCK)
        .transactionType(transactionType)
        .quantity(BigDecimal.ZERO)
        .pricePerUnit(BigDecimal.ZERO)
        .totalValue(totalValue)
        .build();
  }

  // ── TEST 5a — DIVIDEND ────────────────────────────────────────────────────

  @Test
  void dividendIsAValidTransactionTypeWithDividendReceivedLabel() {
    UUID userId = UUID.randomUUID();
    Transaction tx = buildTransaction("DIVIDEND", new BigDecimal("125.50"));
    when(transactionRepository.findByTransactionIdAndUserId(tx.getTransactionId(), userId))
        .thenReturn(Optional.of(tx));

    TransactionDetailsResponse details =
        service.getTransactionDetails(userId, tx.getTransactionId());

    assertThat(details.transactionType()).isEqualTo("DIVIDEND");
    assertThat(details.amountLabel()).isEqualTo("Dividend Received");
    // netAmount = grossAmount - fee = 125.50 - 0.00 = 125.50
    assertThat(details.netAmount()).isEqualByComparingTo(new BigDecimal("125.50"));
  }

  @Test
  void dividendNetAmountSubtractsFee() {
    UUID userId = UUID.randomUUID();
    Transaction tx =
        Transaction.builder()
            .transactionId(UUID.randomUUID())
            .assetSymbol("MSFT")
            .assetType(AssetType.STOCK)
            .transactionType("DIVIDEND")
            .quantity(BigDecimal.ZERO)
            .pricePerUnit(BigDecimal.ZERO)
            .totalValue(new BigDecimal("100.00"))
            .fee(new BigDecimal("10.00"))
            .build();
    when(transactionRepository.findByTransactionIdAndUserId(tx.getTransactionId(), userId))
        .thenReturn(Optional.of(tx));

    TransactionDetailsResponse details =
        service.getTransactionDetails(userId, tx.getTransactionId());

    assertThat(details.netAmount()).isEqualByComparingTo(new BigDecimal("90.00"));
  }

  // ── TEST 5a — REDEEM ─────────────────────────────────────────────────────

  @Test
  void redeemIsAValidTransactionTypeWithAmountRedeemedLabel() {
    UUID userId = UUID.randomUUID();
    Transaction tx = buildTransaction("REDEEM", new BigDecimal("18524.32"));
    when(transactionRepository.findByTransactionIdAndUserId(tx.getTransactionId(), userId))
        .thenReturn(Optional.of(tx));

    TransactionDetailsResponse details =
        service.getTransactionDetails(userId, tx.getTransactionId());

    assertThat(details.transactionType()).isEqualTo("REDEEM");
    assertThat(details.amountLabel()).isEqualTo("Amount Redeemed");
    // For REDEEM netAmount = grossAmount (no fee deduction)
    assertThat(details.netAmount()).isEqualByComparingTo(new BigDecimal("18524.32"));
  }
}
