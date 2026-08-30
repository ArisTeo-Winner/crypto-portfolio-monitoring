package com.mx.cryptomonitor.statementimport.infrastructure.scheduling;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.mx.cryptomonitor.statementimport.application.dto.response.StatementImportResult;
import com.mx.cryptomonitor.statementimport.application.port.in.ImportDriveWealthConfirmationsUseCase;
import com.mx.cryptomonitor.statementimport.application.port.in.ImportGbmEquityConfirmationsUseCase;
import com.mx.cryptomonitor.statementimport.application.port.in.ImportGbmStatementUseCase;
import com.mx.cryptomonitor.statementimport.application.port.out.BrokerDocumentDetectorPort;
import com.mx.cryptomonitor.statementimport.application.service.StatementImportJobLifecycleService;
import com.mx.cryptomonitor.statementimport.domain.exception.InvalidStatementDocumentException;
import com.mx.cryptomonitor.statementimport.domain.model.StatementImportJob;
import com.mx.cryptomonitor.statementimport.domain.model.StatementImportJobStatus;
import com.mx.cryptomonitor.statementimport.domain.model.StatementImportJobType;
import com.mx.cryptomonitor.statementimport.domain.model.UploadedDocument;

@ExtendWith(MockitoExtension.class)
class StatementImportJobWorkerTest {

  @Mock private StatementImportJobLifecycleService lifecycleService;
  @Mock private ImportGbmStatementUseCase importGbmStatementUseCase;
  @Mock private ImportDriveWealthConfirmationsUseCase importDriveWealthConfirmationsUseCase;
  @Mock private ImportGbmEquityConfirmationsUseCase importGbmEquityConfirmationsUseCase;
  @Mock private BrokerDocumentDetectorPort brokerDocumentDetector;

  private StatementImportJobWorker worker;

  @BeforeEach
  void setUp() {
    worker =
        new StatementImportJobWorker(
            lifecycleService,
            importGbmStatementUseCase,
            importDriveWealthConfirmationsUseCase,
            importGbmEquityConfirmationsUseCase,
            brokerDocumentDetector);
    ReflectionTestUtils.setField(worker, "batchSize", 5);
  }

  @Test
  void autoDetectResolvesTypeAndRoutesToDetectedImportService() {
    StatementImportJob job =
        StatementImportJob.builder()
            .id(UUID.randomUUID())
            .userId(UUID.randomUUID())
            .jobType(StatementImportJobType.AUTO_DETECT)
            .status(StatementImportJobStatus.PROCESSING)
            .fileName("fmty.pdf")
            .fileContent(new byte[] {1})
            .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
            .build();
    StatementImportResult result = new StatementImportResult("fmty.pdf", 1, 0, 0, 0, List.of());
    when(lifecycleService.claimBatch(5)).thenReturn(List.of(job));
    when(brokerDocumentDetector.detect(job.getFileContent()))
        .thenReturn(StatementImportJobType.GBM_EQUITY_CONFIRMATION);
    when(importGbmEquityConfirmationsUseCase.importConfirmations(eq(job.getUserId()), any()))
        .thenReturn(List.of(result));

    worker.processQueuedJobs();

    verify(importGbmEquityConfirmationsUseCase).importConfirmations(eq(job.getUserId()), any());
    verify(lifecycleService).markCompleted(job.getId(), result);
  }

  @Test
  void dataErrorMarksJobFailedAsPermanent() {
    StatementImportJob job = gbmJob();
    when(lifecycleService.claimBatch(5)).thenReturn(List.of(job));
    when(importGbmStatementUseCase.importStatement(
            eq(job.getUserId()), any(UploadedDocument.class)))
        .thenThrow(new InvalidStatementDocumentException("PDF no reconocido"));

    worker.processQueuedJobs();

    verify(lifecycleService).markFailed(job.getId(), "PDF no reconocido", true);
  }

  @Test
  void transientErrorMarksJobFailedAsRetryable() {
    StatementImportJob job = gbmJob();
    when(lifecycleService.claimBatch(5)).thenReturn(List.of(job));
    when(importGbmStatementUseCase.importStatement(
            eq(job.getUserId()), any(UploadedDocument.class)))
        .thenThrow(new IllegalStateException("timeout de red"));

    worker.processQueuedJobs();

    verify(lifecycleService).markFailed(job.getId(), "timeout de red", false);
  }

  @Test
  void successfulImportMarksJobCompleted() {
    StatementImportJob job = gbmJob();
    StatementImportResult result = new StatementImportResult("estado.pdf", 1, 0, 0, 0, List.of());
    when(lifecycleService.claimBatch(5)).thenReturn(List.of(job));
    when(importGbmStatementUseCase.importStatement(
            eq(job.getUserId()), any(UploadedDocument.class)))
        .thenReturn(result);

    worker.processQueuedJobs();

    verify(lifecycleService).markCompleted(job.getId(), result);
  }

  private StatementImportJob gbmJob() {
    return StatementImportJob.builder()
        .id(UUID.randomUUID())
        .userId(UUID.randomUUID())
        .jobType(StatementImportJobType.GBM_STATEMENT)
        .status(StatementImportJobStatus.PROCESSING)
        .fileName("estado.pdf")
        .fileContent(new byte[] {1})
        .createdAt(OffsetDateTime.now(ZoneOffset.UTC))
        .build();
  }
}
