package com.mx.cryptomonitor.statementimport.application.port.out;

import com.mx.cryptomonitor.statementimport.domain.model.ParsedGbmEquityConfirmationRow;

public interface GbmEquityConfirmationParserPort {

  ParsedGbmEquityConfirmationRow parse(byte[] pdfContent);
}
