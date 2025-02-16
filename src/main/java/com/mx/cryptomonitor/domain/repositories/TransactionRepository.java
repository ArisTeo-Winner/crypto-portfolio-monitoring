package com.mx.cryptomonitor.domain.repositories;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.mx.cryptomonitor.domain.models.Transaction;
import com.mx.cryptomonitor.shared.dto.response.TransactionResponse;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    List<TransactionResponse> findByUserId(UUID userId);
    List<TransactionResponse> findByUserIdAndAssetSymbol(UUID userId, String assetSymbol);
    
}