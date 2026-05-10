package com.mx.cryptomonitor.transaction.application.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mx.cryptomonitor.transaction.domain.model.Transaction;
import com.mx.cryptomonitor.transaction.domain.repository.TransactionRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TransactionRealizedPnlService {

  private static final int COST_SCALE = 8;
  private static final int MONEY_SCALE = 2;

  private final TransactionRepository transactionRepository;

  @Transactional
  public void rebuildUserRealizedPnl(UUID userId) {
    List<Transaction> transactions = transactionRepository.findByUserIdOrderByTransactionDateAscCreatedAtAsc(userId);
    Map<String, CostBasisState> statesByAsset = new LinkedHashMap<>();

    for (Transaction transaction : transactions) {
      String transactionType = normalize(transaction.getTransactionType());
      CostBasisState state =
          statesByAsset.computeIfAbsent(
              transaction.getAssetSymbol().toUpperCase(), ignored -> new CostBasisState());

      if ("BUY".equals(transactionType)) {
        BigDecimal grossAmount = grossAmount(transaction);
        state.quantity = state.quantity.add(amount(transaction.getQuantity()));
        state.openCostBasis = state.openCostBasis.add(grossAmount).add(amount(transaction.getFee()));
        transaction.setRealizedPnl(BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
        continue;
      }

      if ("SELL".equals(transactionType)) {
        BigDecimal soldQuantity = min(amount(transaction.getQuantity()), state.quantity);
        BigDecimal averageCost = averageCost(state);
        BigDecimal realized =
            grossAmount(transaction).subtract(averageCost.multiply(soldQuantity)).subtract(amount(transaction.getFee()));
        transaction.setRealizedPnl(scaleMoney(realized));
        state.quantity = state.quantity.subtract(soldQuantity);
        state.openCostBasis = clampToZero(state.openCostBasis.subtract(averageCost.multiply(soldQuantity)));
        if (state.quantity.compareTo(BigDecimal.ZERO) == 0) {
          state.openCostBasis = BigDecimal.ZERO;
        }
        continue;
      }

      transaction.setRealizedPnl(BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP));
    }

    transactionRepository.saveAll(transactions);
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim().toUpperCase();
  }

  private BigDecimal grossAmount(Transaction transaction) {
    BigDecimal totalValue = amount(transaction.getTotalValue());
    if (totalValue.compareTo(BigDecimal.ZERO) > 0) {
      return totalValue;
    }
    return amount(transaction.getQuantity()).multiply(amount(transaction.getPricePerUnit()));
  }

  private BigDecimal averageCost(CostBasisState state) {
    if (state.quantity.compareTo(BigDecimal.ZERO) <= 0) {
      return BigDecimal.ZERO;
    }
    return state.openCostBasis.divide(state.quantity, COST_SCALE, RoundingMode.HALF_UP);
  }

  private BigDecimal amount(BigDecimal value) {
    return value != null ? value : BigDecimal.ZERO;
  }

  private BigDecimal min(BigDecimal left, BigDecimal right) {
    return left.compareTo(right) <= 0 ? left : right;
  }

  private BigDecimal clampToZero(BigDecimal value) {
    return value.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : value;
  }

  private BigDecimal scaleMoney(BigDecimal value) {
    return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
  }

  private static final class CostBasisState {
    private BigDecimal quantity = BigDecimal.ZERO;
    private BigDecimal openCostBasis = BigDecimal.ZERO;
  }
}
