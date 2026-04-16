package com.mx.cryptomonitor.transaction.infrastructure.outbound.portfolio;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.mx.cryptomonitor.portfolio.application.port.out.PortfolioTransactionSnapshot;
import com.mx.cryptomonitor.portfolio.application.port.out.TransactionHistoryPort;
import com.mx.cryptomonitor.transaction.domain.repository.TransactionRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TransactionHistoryAdapter implements TransactionHistoryPort {

  private final TransactionRepository transactionRepository;

  @Override
  public List<PortfolioTransactionSnapshot> getTransactionsByUser(UUID userId) {
    return transactionRepository.findByUserIdOrderByTransactionDateAscCreatedAtAsc(userId).stream()
        .map(
            transaction ->
                new PortfolioTransactionSnapshot(
                    transaction.getAssetSymbol(),
                    transaction.getAssetType().name(),
                    transaction.getTransactionType(),
                    transaction.getTransferType(),
                    transaction.getQuantity(),
                    transaction.getTotalValue(),
                    transaction.getPricePerUnit(),
                    transaction.getFee(),
                    transaction.getTransactionDate()))
        .toList();
  }
}
