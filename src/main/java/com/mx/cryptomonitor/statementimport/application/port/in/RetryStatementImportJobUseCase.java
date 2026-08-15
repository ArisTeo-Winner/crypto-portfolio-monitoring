package com.mx.cryptomonitor.statementimport.application.port.in;

import java.util.UUID;

import com.mx.cryptomonitor.statementimport.application.dto.response.StatementImportJobResponse;

public interface RetryStatementImportJobUseCase {

  StatementImportJobResponse retry(UUID userId, UUID jobId);
}
