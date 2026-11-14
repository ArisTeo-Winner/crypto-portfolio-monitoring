package com.mx.cryptomonitor.marketdata.domain.model;

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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Job de la cola durable de procesamiento de dividendos (ADR-0028 F2): al registrar una compra se
 * encola un símbolo y un worker lo drena (sync + replay de accrual) con retry/backoff y
 * dead-letter, sobreviviendo reinicios. Mismo patrón Postgres-as-queue que {@code
 * statement_import_job}.
 */
@Entity
@Table(name = "dividend_sync_job")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DividendSyncJob {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "asset_symbol", nullable = false, length = 20)
  private String assetSymbol;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private DividendSyncJobStatus status;

  @Column(name = "attempt_count", nullable = false)
  @Builder.Default
  private int attemptCount = 0;

  @Column(name = "next_attempt_at")
  private OffsetDateTime nextAttemptAt;

  @Column(name = "error_message", columnDefinition = "TEXT")
  private String errorMessage;

  @Column(name = "created_at", nullable = false)
  private OffsetDateTime createdAt;

  @Column(name = "started_at")
  private OffsetDateTime startedAt;

  @Column(name = "completed_at")
  private OffsetDateTime completedAt;
}
