package com.mx.cryptomonitor.statementimport.application.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

public record StatementImportJobResponse(
    UUID jobId,
    String fileName,
    String jobType,
    String status,
    StatementImportResult result,
    String errorMessage,
    int attemptCount,
    OffsetDateTime createdAt,
    OffsetDateTime completedAt) {}
