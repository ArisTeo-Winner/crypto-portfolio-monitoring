package com.mx.cryptomonitor.statementimport.application.port.in;

import java.util.UUID;

import com.mx.cryptomonitor.statementimport.application.dto.response.StatementImportResult;
import com.mx.cryptomonitor.statementimport.domain.model.UploadedDocument;

public interface ImportGbmStatementUseCase {

  StatementImportResult importStatement(UUID userId, UploadedDocument document);
}
