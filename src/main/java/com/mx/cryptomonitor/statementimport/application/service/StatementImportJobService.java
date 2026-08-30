package com.mx.cryptomonitor.statementimport.application.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mx.cryptomonitor.statementimport.application.dto.response.StatementImportJobResponse;
import com.mx.cryptomonitor.statementimport.application.dto.response.StatementImportResult;
import com.mx.cryptomonitor.statementimport.application.port.in.EnqueueStatementImportUseCase;
import com.mx.cryptomonitor.statementimport.application.port.in.RetryStatementImportJobUseCase;
import com.mx.cryptomonitor.statementimport.application.port.in.StatementImportJobQueryUseCase;
import com.mx.cryptomonitor.statementimport.domain.exception.InvalidStatementDocumentException;
import com.mx.cryptomonitor.statementimport.domain.exception.StatementImportJobNotFoundException;
import com.mx.cryptomonitor.statementimport.domain.exception.StatementImportJobNotRetryableException;
import com.mx.cryptomonitor.statementimport.domain.model.StatementImportJob;
import com.mx.cryptomonitor.statementimport.domain.model.StatementImportJobStatus;
import com.mx.cryptomonitor.statementimport.domain.model.StatementImportJobType;
import com.mx.cryptomonitor.statementimport.domain.model.UploadedDocument;
import com.mx.cryptomonitor.statementimport.domain.repository.StatementImportJobRepository;

import lombok.RequiredArgsConstructor;

/**
 * Encola, consulta y reintenta jobs manualmente — nunca parsea PDFs ni llama a
 * TransactionCommandUseCase. El parseo real sigue viviendo en
 * GbmStatementImportService/DriveWealthImportService, invocados desde StatementImportJobWorker.
 */
@Service
@RequiredArgsConstructor
public class StatementImportJobService
    implements EnqueueStatementImportUseCase,
        StatementImportJobQueryUseCase,
        RetryStatementImportJobUseCase {

  private final StatementImportJobRepository jobRepository;
  private final ObjectMapper objectMapper;

  @Override
  public StatementImportJobResponse enqueueGbmStatement(UUID userId, UploadedDocument document) {
    StatementImportJob job = enqueue(userId, StatementImportJobType.GBM_STATEMENT, document);
    return toResponse(job);
  }

  @Override
  public List<StatementImportJobResponse> enqueueDriveWealthConfirmations(
      UUID userId, List<UploadedDocument> documents) {
    return documents.stream()
        .map(document -> enqueue(userId, StatementImportJobType.DRIVEWEALTH_CONFIRMATION, document))
        .map(this::toResponse)
        .toList();
  }

  @Override
  public List<StatementImportJobResponse> enqueueAutoDetected(
      UUID userId, List<UploadedDocument> documents) {
    return documents.stream()
        .map(document -> enqueue(userId, StatementImportJobType.AUTO_DETECT, document))
        .map(this::toResponse)
        .toList();
  }

  @Override
  public StatementImportJobResponse getJob(UUID userId, UUID jobId) {
    StatementImportJob job =
        jobRepository
            .findByIdAndUserId(jobId, userId)
            .orElseThrow(
                () ->
                    new StatementImportJobNotFoundException(
                        "No se encontro el job de importacion solicitado"));
    return toResponse(job);
  }

  @Override
  public List<StatementImportJobResponse> listRecentJobs(UUID userId) {
    return jobRepository.findFirst20ByUserIdOrderByCreatedAtDesc(userId).stream()
        .map(this::toResponse)
        .toList();
  }

  @Override
  public StatementImportJobResponse retry(UUID userId, UUID jobId) {
    StatementImportJob job =
        jobRepository
            .findByIdAndUserId(jobId, userId)
            .orElseThrow(
                () ->
                    new StatementImportJobNotFoundException(
                        "No se encontro el job de importacion solicitado"));
    if (job.getStatus() != StatementImportJobStatus.DEAD_LETTER) {
      throw new StatementImportJobNotRetryableException(
          "Solo se pueden reintentar jobs en estado DEAD_LETTER (actual: " + job.getStatus() + ")");
    }
    job.setStatus(StatementImportJobStatus.QUEUED);
    job.setNextAttemptAt(null);
    job.setCompletedAt(null);
    return toResponse(jobRepository.save(job));
  }

  private StatementImportJob enqueue(
      UUID userId, StatementImportJobType jobType, UploadedDocument document) {
    StatementImportJob job =
        StatementImportJob.builder()
            .userId(userId)
            .jobType(jobType)
            .status(StatementImportJobStatus.QUEUED)
            .fileName(document.fileName())
            .fileContent(document.content())
            .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
            .build();
    return jobRepository.save(job);
  }

  private StatementImportJobResponse toResponse(StatementImportJob job) {
    return new StatementImportJobResponse(
        job.getId(),
        job.getFileName(),
        job.getJobType().name(),
        job.getStatus().name(),
        deserializeResult(job.getResultJson()),
        job.getErrorMessage(),
        job.getAttemptCount(),
        job.getCreatedAt(),
        job.getCompletedAt());
  }

  private StatementImportResult deserializeResult(String resultJson) {
    if (resultJson == null) {
      return null;
    }
    try {
      return objectMapper.readValue(resultJson, StatementImportResult.class);
    } catch (JsonProcessingException ex) {
      throw new InvalidStatementDocumentException("No se pudo leer el resultado del job", ex);
    }
  }
}
