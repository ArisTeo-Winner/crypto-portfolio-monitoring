package com.mx.cryptomonitor.transaction.infrastructure.outbound.portfolio;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.mx.cryptomonitor.portfolio.application.port.out.PortfolioTransactionSnapshot;
import com.mx.cryptomonitor.portfolio.application.port.out.AssetTransactionHistoryPort;
import com.mx.cryptomonitor.portfolio.application.port.out.PortfolioMarkersPort;
import com.mx.cryptomonitor.portfolio.application.port.out.TransactionHistoryPort;
import com.mx.cryptomonitor.transaction.domain.repository.TransactionRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TransactionHistoryAdapter
    implements TransactionHistoryPort, AssetTransactionHistoryPort, PortfolioMarkersPort {

  private final TransactionRepository transactionRepository;

  @Override
  public List<PortfolioTransactionSnapshot> getTransactionsByUser(UUID userId) {
    return transactionRepository.findByUserIdOrderByTransactionDateAscCreatedAtAsc(userId).stream()
        .map(this::toSnapshot)
        .toList();
  }

  @Override
  public List<PortfolioTransactionSnapshot> getTransactionsByUserAndSymbol(
      UUID userId, String symbol) {
    return transactionRepository
        .findByUserIdAndAssetSymbolOrderByTransactionDateAscCreatedAtAsc(
            userId, symbol.trim().toUpperCase())
        .stream()
        .map(this::toSnapshot)
        .toList();
  }

  private PortfolioTransactionSnapshot toSnapshot(
      com.mx.cryptomonitor.transaction.domain.model.Transaction transaction) {
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
        transaction.getTransactionDate());
  }
}
