package com.mx.cryptomonitor.transaction.domain.model;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "transaction_idempotency_keys",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_transaction_idempotency_user_scope_key",
            columnNames = {"user_id", "operation_scope", "idempotency_key"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransactionIdempotencyRecord {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "record_id", nullable = false, updatable = false)
  private UUID recordId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "operation_scope", nullable = false, length = 160)
  private String operationScope;

  @Column(name = "idempotency_key", nullable = false, length = 200)
  private String idempotencyKey;

  @Column(name = "request_hash", nullable = false, length = 64)
  private String requestHash;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private TransactionIdempotencyStatus status;

  @Column(name = "result_transaction_id")
  private UUID resultTransactionId;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "completed_at")
  private OffsetDateTime completedAt;

  @Column(name = "expires_at", nullable = false)
  private OffsetDateTime expiresAt;
}
