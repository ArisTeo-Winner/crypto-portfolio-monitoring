package com.mx.cryptomonitor.statementimport.infrastructure.scheduling;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.mx.cryptomonitor.statementimport.application.dto.response.StatementImportResult;
import com.mx.cryptomonitor.statementimport.application.port.in.ImportDriveWealthConfirmationsUseCase;
import com.mx.cryptomonitor.statementimport.application.port.in.ImportGbmEquityConfirmationsUseCase;
import com.mx.cryptomonitor.statementimport.application.port.in.ImportGbmStatementUseCase;
import com.mx.cryptomonitor.statementimport.application.port.out.BrokerDocumentDetectorPort;
import com.mx.cryptomonitor.statementimport.application.service.StatementImportJobLifecycleService;
import com.mx.cryptomonitor.statementimport.domain.exception.InvalidStatementDocumentException;
import com.mx.cryptomonitor.statementimport.domain.exception.UnrecognizedBrokerDocumentException;
import com.mx.cryptomonitor.statementimport.domain.model.StatementImportJob;
import com.mx.cryptomonitor.statementimport.domain.model.StatementImportJobType;
import com.mx.cryptomonitor.statementimport.domain.model.UploadedDocument;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Reclama jobs QUEUED (Postgres como cola via FOR UPDATE SKIP LOCKED, ver
 * StatementImportJobRepository.lockCandidates) y los procesa llamando, sin cambios, a los mismos
 * casos de uso que antes se invocaban directamente desde el controlador de forma sincrona.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StatementImportJobWorker {

  private final StatementImportJobLifecycleService lifecycleService;
  private final ImportGbmStatementUseCase importGbmStatementUseCase;
  private final ImportDriveWealthConfirmationsUseCase importDriveWealthConfirmationsUseCase;
  private final ImportGbmEquityConfirmationsUseCase importGbmEquityConfirmationsUseCase;
  private final BrokerDocumentDetectorPort brokerDocumentDetector;

  @Value("${statementimport.worker.batch-size:5}")
  private int batchSize;

  @Scheduled(fixedDelayString = "${statementimport.worker.poll-interval-ms:2000}")
  public void processQueuedJobs() {
    List<StatementImportJob> claimed = lifecycleService.claimBatch(batchSize);
    claimed.forEach(this::processJob);
  }

  private void processJob(StatementImportJob job) {
    UploadedDocument document = new UploadedDocument(job.getFileName(), job.getFileContent());
    try {
      StatementImportJobType effectiveType =
          job.getJobType() == StatementImportJobType.AUTO_DETECT
              ? brokerDocumentDetector.detect(job.getFileContent())
              : job.getJobType();
      StatementImportResult result =
          switch (effectiveType) {
            case GBM_STATEMENT -> importGbmStatementUseCase.importStatement(
                job.getUserId(), document);
            case DRIVEWEALTH_CONFIRMATION -> importDriveWealthConfirmationsUseCase
                .importConfirmations(job.getUserId(), List.of(document))
                .get(0);
            case GBM_EQUITY_CONFIRMATION -> importGbmEquityConfirmationsUseCase
                .importConfirmations(job.getUserId(), List.of(document))
                .get(0);
            case AUTO_DETECT -> throw new IllegalStateException(
                "AUTO_DETECT ya deberia estar resuelto");
          };
      lifecycleService.markCompleted(job.getId(), result);
    } catch (RuntimeException ex) {
      log.warn("Fallo al procesar statement import job {}", job.getId(), ex);
      lifecycleService.markFailed(job.getId(), ex.getMessage(), isPermanent(ex));
    }
  }

  /**
   * Errores de datos (PDF ilegible, broker no reconocido) no se resuelven reintentando: el mismo
   * archivo produce el mismo error siempre. Cualquier otra RuntimeException (red, DB) se trata como
   * transitoria y puede reintentarse.
   */
  private boolean isPermanent(RuntimeException ex) {
    return ex instanceof InvalidStatementDocumentException
        || ex instanceof UnrecognizedBrokerDocumentException;
  }
}
