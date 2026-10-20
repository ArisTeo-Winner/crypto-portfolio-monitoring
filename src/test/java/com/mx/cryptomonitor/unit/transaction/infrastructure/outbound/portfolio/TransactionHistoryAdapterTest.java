package com.mx.cryptomonitor.unit.transaction.infrastructure.outbound.portfolio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mx.cryptomonitor.portfolio.application.port.out.PortfolioTransactionSnapshot;
import com.mx.cryptomonitor.transaction.domain.model.AssetType;
import com.mx.cryptomonitor.transaction.domain.model.Transaction;
import com.mx.cryptomonitor.transaction.domain.repository.DividendDetailRepository;
import com.mx.cryptomonitor.transaction.domain.repository.TransactionRepository;
import com.mx.cryptomonitor.transaction.infrastructure.outbound.portfolio.TransactionHistoryAdapter;

@ExtendWith(MockitoExtension.class)
class TransactionHistoryAdapterTest {

  @Mock private TransactionRepository transactionRepository;
  @Mock private DividendDetailRepository dividendDetailRepository;

  private TransactionHistoryAdapter adapter() {
    return new TransactionHistoryAdapter(transactionRepository, dividendDetailRepository);
  }

  private Transaction tx(UUID id, String type, String totalValue) {
    return Transaction.builder()
        .transactionId(id)
        .assetSymbol("GOOGL")
        .assetType(AssetType.STOCK)
        .transactionType(type)
        .quantity(BigDecimal.ZERO)
        .totalValue(new BigDecimal(totalValue))
        .pricePerUnit(BigDecimal.ZERO)
        .fee(BigDecimal.ZERO)
        .realizedPnl(BigDecimal.ZERO)
        .transactionDate(OffsetDateTime.now(ZoneOffset.UTC))
        .currency("USD")
        .build();
  }

  @Test
  void populatesTaxWithheldOnDividendSnapshots() {
    UUID buyId = UUID.randomUUID();
    UUID divId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();

    when(transactionRepository.findByUserIdOrderByTransactionDateAscCreatedAtAsc(userId))
        .thenReturn(List.of(tx(buyId, "BUY", "140.00"), tx(divId, "DIVIDEND", "20.00")));
    when(dividendDetailRepository.findTaxWithheldByUserId(userId))
        .thenReturn(List.<Object[]>of(new Object[] {divId, new BigDecimal("3.80")}));

    List<PortfolioTransactionSnapshot> snapshots = adapter().getTransactionsByUser(userId);

    PortfolioTransactionSnapshot buy =
        snapshots.stream().filter(s -> "BUY".equals(s.transactionType())).findFirst().orElseThrow();
    PortfolioTransactionSnapshot dividend =
        snapshots.stream()
            .filter(s -> "DIVIDEND".equals(s.transactionType()))
            .findFirst()
            .orElseThrow();

    assertThat(buy.taxWithheld()).isNull();
    assertThat(dividend.taxWithheld()).isEqualByComparingTo("3.80");
  }
}
