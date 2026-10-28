package com.mx.cryptomonitor.transaction.infrastructure.outbound.portfolio;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.mx.cryptomonitor.portfolio.application.port.out.AssetTransactionHistoryPort;
import com.mx.cryptomonitor.portfolio.application.port.out.PortfolioMarkersPort;
import com.mx.cryptomonitor.portfolio.application.port.out.PortfolioTransactionSnapshot;
import com.mx.cryptomonitor.portfolio.application.port.out.TransactionHistoryPort;
import com.mx.cryptomonitor.transaction.domain.model.Transaction;
import com.mx.cryptomonitor.transaction.domain.repository.DividendDetailRepository;
import com.mx.cryptomonitor.transaction.domain.repository.TransactionRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TransactionHistoryAdapter
    implements TransactionHistoryPort, AssetTransactionHistoryPort, PortfolioMarkersPort {

  private final TransactionRepository transactionRepository;
  private final DividendDetailRepository dividendDetailRepository;

  @Override
  public List<PortfolioTransactionSnapshot> getTransactionsByUser(UUID userId) {
    Map<UUID, BigDecimal> taxByTransaction = taxWithheldByTransaction(userId);
    return transactionRepository.findByUserIdOrderByTransactionDateAscCreatedAtAsc(userId).stream()
        .map(transaction -> toSnapshot(transaction, taxByTransaction))
        .toList();
  }

  @Override
  public List<PortfolioTransactionSnapshot> getTransactionsByUserAndSymbol(
      UUID userId, String symbol) {
    Map<UUID, BigDecimal> taxByTransaction = taxWithheldByTransaction(userId);
    return transactionRepository
        .findByUserIdAndAssetSymbolOrderByTransactionDateAscCreatedAtAsc(
            userId, symbol.trim().toUpperCase())
        .stream()
        .map(transaction -> toSnapshot(transaction, taxByTransaction))
        .toList();
  }

  private Map<UUID, BigDecimal> taxWithheldByTransaction(UUID userId) {
    return dividendDetailRepository.findTaxWithheldByUserId(userId).stream()
        .filter(row -> row[0] != null && row[1] != null)
        .collect(Collectors.toMap(row -> (UUID) row[0], row -> (BigDecimal) row[1]));
  }

  private PortfolioTransactionSnapshot toSnapshot(
      Transaction transaction, Map<UUID, BigDecimal> taxByTransaction) {
    return new PortfolioTransactionSnapshot(
        transaction.getAssetSymbol(),
        transaction.getAssetType().name(),
        transaction.getTransactionType(),
        transaction.getTransferType(),
        transaction.getQuantity(),
        transaction.getTotalValue(),
        transaction.getPricePerUnit(),
        transaction.getFee(),
        transaction.getRealizedPnl(),
        transaction.getTransactionDate(),
        transaction.getCurrency(),
        taxByTransaction.get(transaction.getTransactionId()));
  }
}
