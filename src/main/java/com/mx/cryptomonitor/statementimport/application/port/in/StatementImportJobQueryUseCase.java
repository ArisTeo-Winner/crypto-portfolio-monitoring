package com.mx.cryptomonitor.statementimport.application.port.in;

import java.util.List;
import java.util.UUID;

import com.mx.cryptomonitor.statementimport.application.dto.response.StatementImportJobResponse;

public interface StatementImportJobQueryUseCase {

  StatementImportJobResponse getJob(UUID userId, UUID jobId);

  List<StatementImportJobResponse> listRecentJobs(UUID userId);
}
