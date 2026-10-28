package com.mx.cryptomonitor.statementimport.application.port.out;

import com.mx.cryptomonitor.statementimport.domain.model.GbmStatementParseResult;

public interface GbmStatementParserPort {

  GbmStatementParseResult parse(byte[] pdfContent);
}
