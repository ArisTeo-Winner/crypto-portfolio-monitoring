package com.mx.cryptomonitor.transaction.domain.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mx.cryptomonitor.transaction.domain.model.TransactionIdempotencyRecord;

public interface TransactionIdempotencyRecordRepository
    extends JpaRepository<TransactionIdempotencyRecord, UUID> {

  Optional<TransactionIdempotencyRecord> findByUserIdAndOperationScopeAndIdempotencyKey(
      UUID userId, String operationScope, String idempotencyKey);
}
