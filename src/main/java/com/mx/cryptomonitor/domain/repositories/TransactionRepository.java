package com.mx.cryptomonitor.domain.repositories;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.mx.cryptomonitor.domain.models.Transaction;
import com.mx.cryptomonitor.shared.dto.response.TransactionResponse;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {
    List<TransactionResponse> findByUserId(UUID userId);
    List<TransactionResponse> findByUserIdAndAssetSymbol(UUID userId, String assetSymbol);
    List<TransactionResponse> findByUserIdAndAssetType(UUID userId, String assetType);
    List<TransactionResponse> findByUserIdAndTransactionType(UUID userId, String transactionType);
    
    //@Query("SELECT t FROM Transaction t WHERE t.user.id = :userId AND t.assetType = :assetType")
    //List<Transaction> findByUserIdAndAssetType(@Param("userId") UUID userId, @Param("assetType") String assetType);
   
    /**/
    @Query("SELECT t FROM Transaction t WHERE t.user.id = :userId AND t.assetSymbol = :assetSymbol AND t.assetType = :assetType AND t.transactionType = :transactionType")
    List<Transaction> findByUserIdAndAssetSymbolAndAssetTypeAndTransactionType(
    		@Param("userId") UUID userId,
    		@Param("assetSymbol") String assetSymbol,
    		@Param("assetType") String assetType,
    		@Param("transactionType") String transactionType);	
}