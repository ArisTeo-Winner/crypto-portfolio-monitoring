package com.mx.cryptomonitor.marketdata.application.service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.mx.cryptomonitor.marketdata.domain.model.DividendSyncJob;
import com.mx.cryptomonitor.marketdata.domain.model.DividendSyncJobStatus;
import com.mx.cryptomonitor.marketdata.domain.repository.DividendSyncJobRepository;

import lombok.RequiredArgsConstructor;

/**
 * Transiciones de la cola durable de dividendos (ADR-0028 F2), cada una en su propia transacción.
 * En bean separado del worker a propósito: {@code @Transactional} solo aplica vía proxy cuando se
 * invoca desde OTRO bean.
 *
 * <p>Reintentos: un fallo (red/proveedor/DB) regresa el job a QUEUED con backoff exponencial hasta
 * agotar {@code max-attempts}; ahí pasa a DEAD_LETTER (el worker ya no lo reclama). No hay "fallo
 * permanente": un símbolo sin dividendos no lanza, solo resuelve NONE.
 */
@Service
@RequiredArgsConstructor
public class DividendSyncJobLifecycleService {

  private static final Duration STALE_PROCESSING_THRESHOLD = Duration.ofMinutes(10);
  private static final Duration BACKOFF_BASE = Duration.ofSeconds(30);
  private static final Duration BACKOFF_CAP = Duration.ofMinutes(15);
  private static final List<DividendSyncJobStatus> ACTIVE =
      List.of(DividendSyncJobStatus.QUEUED, DividendSyncJobStatus.PROCESSING);

  private final DividendSyncJobRepository jobRepository;

  @Value("${dividends.sync-worker.max-attempts:5}")
  private int maxAttempts;

  /**
   * Encola el símbolo si no hay ya un job activo (QUEUED/PROCESSING) para él (dedup/coalesce). El
   * {@code exists} atrapa el caso común; el índice único parcial {@code
   * uq_dividend_sync_job_active_ symbol} (ADR-0028 F4) garantiza la unicidad aun bajo la carrera de
   * dos enqueue concurrentes — la violación se trata como "ya encolado".
   */
  @Transactional
  public void enqueue(String symbol) {
    if (!StringUtils.hasText(symbol)) {
      return;
    }
    String normalized = symbol.trim().toUpperCase();
    if (jobRepository.existsByAssetSymbolAndStatusIn(normalized, ACTIVE)) {
      return;
    }
    try {
      jobRepository.save(
          DividendSyncJob.builder()
              .assetSymbol(normalized)
              .status(DividendSyncJobStatus.QUEUED)
              .attemptCount(0)
              .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
              .build());
    } catch (DataIntegrityViolationException ex) {
      // Carrera: otro enqueue ya insertó un job activo para el símbolo (lo garantiza el índice
      // único parcial). Idempotente: no hay nada que hacer, el símbolo ya está encolado.
    }
  }

  @Transactional
  public List<DividendSyncJob> claimBatch(int limit) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    OffsetDateTime staleBefore = now.minus(STALE_PROCESSING_THRESHOLD);
    List<DividendSyncJob> claimed = jobRepository.lockCandidates(now, staleBefore, limit);
    claimed.forEach(
        job -> {
          job.setStatus(DividendSyncJobStatus.PROCESSING);
          job.setStartedAt(now);
        });
    return jobRepository.saveAll(claimed);
  }

  @Transactional
  public void markCompleted(UUID jobId) {
    jobRepository
        .findById(jobId)
        .ifPresent(
            job -> {
              job.setStatus(DividendSyncJobStatus.COMPLETED);
              job.setCompletedAt(OffsetDateTime.now(ZoneOffset.UTC));
              job.setErrorMessage(null);
              jobRepository.save(job);
            });
  }

  @Transactional
  public void markFailed(UUID jobId, String errorMessage) {
    jobRepository
        .findById(jobId)
        .ifPresent(
            job -> {
              int attempts = job.getAttemptCount() + 1;
              job.setAttemptCount(attempts);
              job.setErrorMessage(errorMessage);
              if (attempts >= maxAttempts) {
                job.setStatus(DividendSyncJobStatus.DEAD_LETTER);
                job.setCompletedAt(OffsetDateTime.now(ZoneOffset.UTC));
                job.setNextAttemptAt(null);
              } else {
                job.setStatus(DividendSyncJobStatus.QUEUED);
                job.setNextAttemptAt(OffsetDateTime.now(ZoneOffset.UTC).plus(backoff(attempts)));
              }
              jobRepository.save(job);
            });
  }

  private Duration backoff(int attemptCount) {
    Duration delay = BACKOFF_BASE.multipliedBy((long) Math.pow(2, attemptCount - 1));
    return delay.compareTo(BACKOFF_CAP) > 0 ? BACKOFF_CAP : delay;
  }
}
