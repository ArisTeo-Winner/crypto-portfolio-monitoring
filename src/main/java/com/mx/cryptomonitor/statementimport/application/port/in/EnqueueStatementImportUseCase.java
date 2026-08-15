package com.mx.cryptomonitor.statementimport.application.port.in;

import java.util.List;
import java.util.UUID;

import com.mx.cryptomonitor.statementimport.application.dto.response.StatementImportJobResponse;
import com.mx.cryptomonitor.statementimport.domain.model.UploadedDocument;

public interface EnqueueStatementImportUseCase {

  StatementImportJobResponse enqueueGbmStatement(UUID userId, UploadedDocument document);

  List<StatementImportJobResponse> enqueueDriveWealthConfirmations(
      UUID userId, List<UploadedDocument> documents);
}
