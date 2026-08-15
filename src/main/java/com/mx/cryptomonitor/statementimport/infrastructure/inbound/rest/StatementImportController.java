package com.mx.cryptomonitor.statementimport.infrastructure.inbound.rest;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.mx.cryptomonitor.statementimport.application.dto.response.StatementImportJobResponse;
import com.mx.cryptomonitor.statementimport.application.port.in.EnqueueStatementImportUseCase;
import com.mx.cryptomonitor.statementimport.application.port.in.RetryStatementImportJobUseCase;
import com.mx.cryptomonitor.statementimport.application.port.in.StatementImportJobQueryUseCase;
import com.mx.cryptomonitor.statementimport.domain.exception.InvalidStatementDocumentException;
import com.mx.cryptomonitor.statementimport.domain.model.UploadedDocument;
import com.mx.cryptomonitor.statementimport.infrastructure.inbound.rest.security.StatementImportRateLimiter;
import com.mx.cryptomonitor.user.application.port.in.CurrentUserPort;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/me/broker/gbm")
@RequiredArgsConstructor
public class StatementImportController {

  private final EnqueueStatementImportUseCase enqueueStatementImportUseCase;
  private final StatementImportJobQueryUseCase statementImportJobQueryUseCase;
  private final RetryStatementImportJobUseCase retryStatementImportJobUseCase;
  private final StatementImportRateLimiter statementImportRateLimiter;
  private final CurrentUserPort currentUserPort;

  @Operation(
      summary = "Cargar estado de cuenta mensual GBM (PDF)",
      description =
          "Encola el estado de cuenta mensual GBM (Smart Cash / Trading Mexico) para "
              + "procesamiento asincrono. Devuelve de inmediato un jobId; el resultado se consulta "
              + "con GET /import-jobs/{jobId}.")
  @PostMapping(value = "/statements", consumes = "multipart/form-data")
  @PreAuthorize("hasRole('USER')")
  public ResponseEntity<StatementImportJobResponse> uploadStatement(
      @RequestParam("file") MultipartFile file,
      Authentication authentication,
      HttpServletRequest request) {
    statementImportRateLimiter.validate(request);
    UUID userId = currentUserPort.resolveUserId(authentication);
    UploadedDocument document = toUploadedDocument(file);
    StatementImportJobResponse job =
        enqueueStatementImportUseCase.enqueueGbmStatement(userId, document);
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(job);
  }

  @Operation(
      summary = "Cargar confirmaciones DriveWealth (PDF, carga multiple)",
      description =
          "Encola una o mas confirmaciones de operacion DriveWealth (Trading Global) para "
              + "procesamiento asincrono, una por archivo. Devuelve un jobId por archivo; el "
              + "resultado de cada uno se consulta con GET /import-jobs/{jobId}.")
  @PostMapping(value = "/drivewealth-confirmations", consumes = "multipart/form-data")
  @PreAuthorize("hasRole('USER')")
  public ResponseEntity<List<StatementImportJobResponse>> uploadDriveWealthConfirmations(
      @RequestParam("files") List<MultipartFile> files,
      Authentication authentication,
      HttpServletRequest request) {
    statementImportRateLimiter.validate(request);
    UUID userId = currentUserPort.resolveUserId(authentication);
    List<UploadedDocument> documents = files.stream().map(this::toUploadedDocument).toList();
    List<StatementImportJobResponse> jobs =
        enqueueStatementImportUseCase.enqueueDriveWealthConfirmations(userId, documents);
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(jobs);
  }

  @Operation(
      summary = "Consultar estado de un job de importacion",
      description =
          "QUEUED -> PROCESSING -> COMPLETED (con result) o DEAD_LETTER (con errorMessage; "
              + "reintentos agotados o error de datos del documento). Un job puede volver de "
              + "PROCESSING a QUEUED varias veces si el error es transitorio (ver attemptCount).")
  @GetMapping("/import-jobs/{jobId}")
  @PreAuthorize("hasRole('USER')")
  public ResponseEntity<StatementImportJobResponse> getImportJob(
      @PathVariable UUID jobId, Authentication authentication) {
    UUID userId = currentUserPort.resolveUserId(authentication);
    return ResponseEntity.ok(statementImportJobQueryUseCase.getJob(userId, jobId));
  }

  @Operation(
      summary = "Listar cargas recientes del usuario autenticado",
      description = "Ultimos 20 jobs de importacion, mas reciente primero.")
  @GetMapping("/import-jobs")
  @PreAuthorize("hasRole('USER')")
  public ResponseEntity<List<StatementImportJobResponse>> listImportJobs(
      Authentication authentication) {
    UUID userId = currentUserPort.resolveUserId(authentication);
    return ResponseEntity.ok(statementImportJobQueryUseCase.listRecentJobs(userId));
  }

  @Operation(
      summary = "Reintentar manualmente un job en DEAD_LETTER",
      description =
          "Vuelve a encolar el job sin re-subir el archivo (el PDF original ya esta guardado). "
              + "Solo aplica a jobs en estado DEAD_LETTER; cualquier otro estado responde 409.")
  @PostMapping("/import-jobs/{jobId}/retry")
  @PreAuthorize("hasRole('USER')")
  public ResponseEntity<StatementImportJobResponse> retryImportJob(
      @PathVariable UUID jobId, Authentication authentication, HttpServletRequest request) {
    statementImportRateLimiter.validate(request);
    UUID userId = currentUserPort.resolveUserId(authentication);
    return ResponseEntity.accepted().body(retryStatementImportJobUseCase.retry(userId, jobId));
  }

  private UploadedDocument toUploadedDocument(MultipartFile file) {
    try {
      return new UploadedDocument(file.getOriginalFilename(), file.getBytes());
    } catch (IOException ex) {
      throw new InvalidStatementDocumentException("No se pudo leer el archivo subido", ex);
    }
  }
}
