package com.mx.cryptomonitor.unit.transaction.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mx.cryptomonitor.transaction.application.service.TransactionRealizedPnlService;
import com.mx.cryptomonitor.transaction.domain.model.AssetType;
import com.mx.cryptomonitor.transaction.domain.model.Transaction;
import com.mx.cryptomonitor.transaction.domain.repository.TransactionRepository;

@ExtendWith(MockitoExtension.class)
class TransactionRealizedPnlServiceTest {

  @Mock private TransactionRepository transactionRepository;
  @InjectMocks private TransactionRealizedPnlService service;

  @Test
  void rebuildUserRealizedPnlUsesAverageCostAndFeesPerUser() {
    UUID userId = UUID.randomUUID();
    Transaction buyOne =
        transaction("SOL", "BUY", "2.0", "100.00", "200.00", "0.50", LocalDateTime.now());
    Transaction buyTwo =
        transaction(
            "SOL", "BUY", "1.0", "130.00", "130.00", "0.00", LocalDateTime.now().plusHours(1));
    Transaction sell =
        transaction(
            "SOL", "SELL", "1.5", "140.00", "210.00", "1.00", LocalDateTime.now().plusHours(2));

    when(transactionRepository.findByUserIdOrderByTransactionDateAscCreatedAtAsc(userId))
        .thenReturn(List.of(buyOne, buyTwo, sell));

    service.rebuildUserRealizedPnl(userId);

    assertThat(buyOne.getRealizedPnl()).isEqualByComparingTo("0.00");
    assertThat(buyTwo.getRealizedPnl()).isEqualByComparingTo("0.00");
    assertThat(sell.getRealizedPnl()).isEqualByComparingTo("43.75");

    ArgumentCaptor<List<Transaction>> captor = ArgumentCaptor.forClass(List.class);
    verify(transactionRepository).saveAll(captor.capture());
    assertThat(captor.getValue()).containsExactly(buyOne, buyTwo, sell);
  }

  private Transaction transaction(
      String assetSymbol,
      String transactionType,
      String quantity,
      String pricePerUnit,
      String totalValue,
      String fee,
      LocalDateTime transactionDate) {
    return Transaction.builder()
        .transactionId(UUID.randomUUID())
        .portfolioEntryId(UUID.randomUUID())
        .assetSymbol(assetSymbol)
        .assetType(AssetType.CRYPTO)
        .transactionType(transactionType)
        .quantity(new BigDecimal(quantity))
        .pricePerUnit(new BigDecimal(pricePerUnit))
        .totalValue(new BigDecimal(totalValue))
        .fee(new BigDecimal(fee))
        .transactionDate(transactionDate)
        .createdAt(transactionDate)
        .updatedAt(transactionDate)
        .build();
  }
}
