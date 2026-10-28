package com.mx.cryptomonitor.statementimport.domain.model;

import java.util.List;

public record GbmStatementParseResult(String contractNumber, List<ParsedStatementRow> rows) {}
