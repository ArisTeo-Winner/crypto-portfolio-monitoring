package com.mx.cryptomonitor.statementimport.application.port.in;

import java.util.List;
import java.util.UUID;

import com.mx.cryptomonitor.statementimport.application.dto.response.StatementImportResult;
import com.mx.cryptomonitor.statementimport.domain.model.UploadedDocument;

public interface ImportDriveWealthConfirmationsUseCase {

  List<StatementImportResult> importConfirmations(UUID userId, List<UploadedDocument> documents);
}
