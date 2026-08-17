package com.mx.cryptomonitor.statementimport.application.service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mx.cryptomonitor.statementimport.application.dto.response.StatementImportResult;
import com.mx.cryptomonitor.statementimport.domain.model.StatementImportJob;
import com.mx.cryptomonitor.statementimport.domain.model.StatementImportJobStatus;
import com.mx.cryptomonitor.statementimport.domain.repository.StatementImportJobRepository;

import lombok.RequiredArgsConstructor;

/**
 * Transiciones de estado de {@link StatementImportJob}, cada una en su propia transaccion. Vive en
 * un bean separado de {@code StatementImportJobWorker} a proposito: los metodos
 * {@code @Transactional} solo aplican via el proxy de Spring cuando se invocan desde OTRO bean,
 * nunca por auto-invocacion dentro de la misma clase.
 *
 * <p>Reintentos: un fallo transitorio (red, DB) devuelve el job a QUEUED con backoff exponencial
 * hasta agotar {@code max-attempts}; un fallo permanente (PDF ilegible, broker no reconocido) o el
 * agotamiento de reintentos lo mueve a DEAD_LETTER, que el worker ya no vuelve a reclamar.
 */
@Service
@RequiredArgsConstructor
public class StatementImportJobLifecycleService {

  private static final Duration STALE_PROCESSING_THRESHOLD = Duration.ofMinutes(10);
  private static final Duration BACKOFF_BASE = Duration.ofSeconds(30);
  private static final Duration BACKOFF_CAP = Duration.ofMinutes(15);

  private final StatementImportJobRepository jobRepository;
  private final ObjectMapper objectMapper;

  @Value("${statementimport.worker.max-attempts:3}")
  private int maxAttempts;

  @Transactional
  public List<StatementImportJob> claimBatch(int limit) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    OffsetDateTime staleBefore = now.minus(STALE_PROCESSING_THRESHOLD);
    List<StatementImportJob> claimed = jobRepository.lockCandidates(now, staleBefore, limit);
    claimed.forEach(
        job -> {
          job.setStatus(StatementImportJobStatus.PROCESSING);
          job.setStartedAt(now);
        });
    return jobRepository.saveAll(claimed);
  }

  @Transactional
  public void markCompleted(UUID jobId, StatementImportResult result) {
    jobRepository
        .findById(jobId)
        .ifPresent(
            job -> {
              job.setStatus(StatementImportJobStatus.COMPLETED);
              job.setResultJson(serialize(result));
              job.setCompletedAt(OffsetDateTime.now(ZoneOffset.UTC));
              jobRepository.save(job);
            });
  }

  /**
   * @param permanent true si el error es de datos (no vale la pena reintentar: mismo PDF, mismo
   *     resultado); false si es transitorio y puede resolverse solo (red, DB).
   */
  @Transactional
  public void markFailed(UUID jobId, String errorMessage, boolean permanent) {
    jobRepository
        .findById(jobId)
        .ifPresent(
            job -> {
              job.setErrorMessage(errorMessage);
              if (permanent) {
                job.setStatus(StatementImportJobStatus.DEAD_LETTER);
                job.setCompletedAt(OffsetDateTime.now(ZoneOffset.UTC));
                job.setNextAttemptAt(null);
              } else if (job.getAttemptCount() + 1 >= maxAttempts) {
                job.setAttemptCount(job.getAttemptCount() + 1);
                job.setStatus(StatementImportJobStatus.DEAD_LETTER);
                job.setCompletedAt(OffsetDateTime.now(ZoneOffset.UTC));
                job.setNextAttemptAt(null);
              } else {
                job.setAttemptCount(job.getAttemptCount() + 1);
                job.setStatus(StatementImportJobStatus.QUEUED);
                job.setNextAttemptAt(
                    OffsetDateTime.now(ZoneOffset.UTC).plus(backoff(job.getAttemptCount())));
              }
              jobRepository.save(job);
            });
  }

  private Duration backoff(int attemptCount) {
    Duration delay = BACKOFF_BASE.multipliedBy((long) Math.pow(2, attemptCount - 1));
    return delay.compareTo(BACKOFF_CAP) > 0 ? BACKOFF_CAP : delay;
  }

  private String serialize(StatementImportResult result) {
    try {
      return objectMapper.writeValueAsString(result);
    } catch (JsonProcessingException ex) {
      throw new IllegalStateException("No se pudo serializar el resultado del import", ex);
    }
  }
}
