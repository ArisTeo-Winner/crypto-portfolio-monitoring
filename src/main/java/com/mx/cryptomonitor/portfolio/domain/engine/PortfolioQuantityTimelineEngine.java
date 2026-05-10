package com.mx.cryptomonitor.portfolio.domain.engine;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.stream.IntStream;

import com.mx.cryptomonitor.portfolio.domain.exception.InsufficientFundsException;
import com.mx.cryptomonitor.portfolio.domain.model.PortfolioAccountingTransaction;
import com.mx.cryptomonitor.portfolio.domain.model.QuantityTimelinePoint;

public class PortfolioQuantityTimelineEngine {

  public List<QuantityTimelinePoint> buildQuantityTimeline(
      List<PortfolioAccountingTransaction> transactions) {
    BigDecimal quantity = BigDecimal.ZERO;
    List<IndexedTransaction> orderedTransactions =
        IntStream.range(0, transactions.size())
            .mapToObj(index -> new IndexedTransaction(index, transactions.get(index)))
            .sorted(
                Comparator.comparing((IndexedTransaction item) -> item.transaction().time())
                    .thenComparingInt(IndexedTransaction::index))
            .toList();

    java.util.ArrayList<QuantityTimelinePoint> timeline = new java.util.ArrayList<>();
    for (IndexedTransaction item : orderedTransactions) {
      PortfolioAccountingTransaction transaction = item.transaction();
      if ("BUY".equalsIgnoreCase(transaction.transactionType())) {
        quantity = quantity.add(amount(transaction.quantity()));
      } else if ("SELL".equalsIgnoreCase(transaction.transactionType())) {
        BigDecimal soldQuantity = amount(transaction.quantity());
        if (quantity.compareTo(soldQuantity) < 0) {
          throw new InsufficientFundsException(
              "Cannot sell more holdings than owned for asset " + transaction.assetSymbol());
        }
        quantity = quantity.subtract(soldQuantity);
      }
      timeline.add(new QuantityTimelinePoint(transaction.time(), quantity));
    }

    return timeline;
  }

  private BigDecimal amount(BigDecimal value) {
    return value != null ? value : BigDecimal.ZERO;
  }

  private record IndexedTransaction(int index, PortfolioAccountingTransaction transaction) {}
}
