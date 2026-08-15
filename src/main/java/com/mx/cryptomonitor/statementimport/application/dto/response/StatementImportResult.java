package com.mx.cryptomonitor.statementimport.application.dto.response;

import java.util.List;

public record StatementImportResult(
    String fileName,
    int accepted,
    int duplicate,
    int skipped,
    int rejected,
    List<String> messages) {}
