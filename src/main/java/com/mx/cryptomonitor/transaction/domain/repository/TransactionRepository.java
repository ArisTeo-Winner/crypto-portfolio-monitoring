package com.mx.cryptomonitor.transaction.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.mx.cryptomonitor.transaction.domain.model.AssetType;
import com.mx.cryptomonitor.transaction.domain.model.Transaction;

@Repository
public interface TransactionRepository
    extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {
  List<Transaction> findByUserId(UUID userId, Sort sort);

  List<Transaction> findByUserIdOrderByTransactionDateAscCreatedAtAsc(UUID userId);

  List<Transaction> findByUserIdAndAssetSymbol(UUID userId, String assetSymbol, Sort sort);

  List<Transaction> findByUserIdAndAssetSymbolOrderByTransactionDateAscCreatedAtAsc(
      UUID userId, String assetSymbol);

  List<Transaction> findByUserIdAndAssetType(UUID userId, AssetType assetType, Sort sort);

  List<Transaction> findByUserIdAndTransactionType(UUID userId, String transactionType, Sort sort);

  Optional<Transaction> findByTransactionIdAndUserId(UUID transactionId, UUID userId);

  @Query(
      "SELECT t FROM Transaction t WHERE t.user.id = :userId AND t.assetSymbol = :assetSymbol AND t.assetType = :assetType AND t.transactionType = :transactionType ORDER BY t.transactionDate DESC, t.createdAt DESC")
  List<Transaction> findByUserIdAndAssetSymbolAndAssetTypeAndTransactionType(
      @Param("userId") UUID userId,
      @Param("assetSymbol") String assetSymbol,
      @Param("assetType") AssetType assetType,
      @Param("transactionType") String transactionType);
}
