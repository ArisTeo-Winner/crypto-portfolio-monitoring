package com.mx.cryptomonitor.unit.transaction.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mx.cryptomonitor.transaction.application.service.DividendReconciliationService;
import com.mx.cryptomonitor.transaction.domain.dividend.DividendEntitlementCalculator;
import com.mx.cryptomonitor.transaction.domain.dividend.DividendTaxCalculator;
import com.mx.cryptomonitor.transaction.domain.model.DividendDetail;
import com.mx.cryptomonitor.transaction.domain.model.DividendOrigin;
import com.mx.cryptomonitor.transaction.domain.model.Transaction;
import com.mx.cryptomonitor.transaction.domain.repository.DividendDetailRepository;
import com.mx.cryptomonitor.transaction.domain.repository.TransactionRepository;
import com.mx.cryptomonitor.user.domain.model.User;

@ExtendWith(MockitoExtension.class)
class DividendReconciliationServiceTest {

  @Mock private DividendDetailRepository dividendDetailRepository;
  @Mock private TransactionRepository transactionRepository;

  private DividendReconciliationService service;
  private final UUID userId = UUID.randomUUID();
  private final LocalDate exDate = LocalDate.of(2026, 6, 16);

  @BeforeEach
  void setUp() {
    service =
        new DividendReconciliationService(
            dividendDetailRepository,
            transactionRepository,
            new DividendEntitlementCalculator(),
            new DividendTaxCalculator());
  }

  private DividendDetail accruedDividend(UUID dividendTxId, BigDecimal gross, BigDecimal dps) {
    Transaction tx = new Transaction();
    tx.setTransactionId(dividendTxId);
    tx.setUser(User.builder().id(userId).build());
    tx.setAssetSymbol("GOOGL");
    tx.setTransactionType("DIVIDEND");
    tx.setTotalValue(gross);
    return DividendDetail.builder()
        .transaction(tx)
        .exDividendDate(exDate)
        .amountPerShare(dps)
        .origin(DividendOrigin.ACCRUED)
        .build();
  }

  private Transaction buy(BigDecimal shares, LocalDate date) {
    Transaction tx = new Transaction();
    tx.setUser(User.builder().id(userId).build());
    tx.setAssetSymbol("GOOGL");
    tx.setTransactionType("BUY");
    tx.setQuantity(shares);
    tx.setTransactionDate(date.atStartOfDay().atOffset(ZoneOffset.UTC));
    return tx;
  }

  @Test
  void deletesDividendWhenNoEntitlementRemains() {
    UUID divId = UUID.randomUUID();
    when(dividendDetailRepository.findAccruedByUserAndSymbol(userId, "GOOGL"))
        .thenReturn(
            List.of(accruedDividend(divId, new BigDecimal("2.00"), new BigDecimal("0.20"))));
    // historial sin compras (la COMPRA fue borrada) -> entitlement 0
    when(transactionRepository.findByUserIdAndAssetSymbol(eq(userId), eq("GOOGL"), any()))
        .thenReturn(List.of());

    service.reconcileAccruedDividends(userId, "googl");

    verify(transactionRepository).deleteById(divId);
    verify(transactionRepository, never()).save(any());
  }

  @Test
  void recomputesAmountWhenEntitlementReducedButPositive() {
    UUID divId = UUID.randomUUID();
    DividendDetail detail =
        accruedDividend(
            divId, new BigDecimal("3.00"), new BigDecimal("0.20")); // viejo: 15 acciones
    when(dividendDetailRepository.findAccruedByUserAndSymbol(userId, "GOOGL"))
        .thenReturn(List.of(detail));
    // queda 1 compra de 10 acciones antes de la ex-date
    when(transactionRepository.findByUserIdAndAssetSymbol(eq(userId), eq("GOOGL"), any()))
        .thenReturn(List.of(buy(new BigDecimal("10"), LocalDate.of(2026, 1, 1))));

    service.reconcileAccruedDividends(userId, "GOOGL");

    verify(transactionRepository, never()).deleteById(any());
    ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
    verify(transactionRepository).save(captor.capture());
    // nuevo bruto = 10 x 0.20 = 2.00
    assertThat(captor.getValue().getTotalValue()).isEqualByComparingTo("2.00");
    verify(dividendDetailRepository).save(detail);
  }

  @Test
  void noopWhenNoAccruedDividends() {
    when(dividendDetailRepository.findAccruedByUserAndSymbol(userId, "GOOGL"))
        .thenReturn(List.of());

    service.reconcileAccruedDividends(userId, "GOOGL");

    verify(transactionRepository, never()).deleteById(any());
    verify(transactionRepository, never()).save(any());
  }
}
