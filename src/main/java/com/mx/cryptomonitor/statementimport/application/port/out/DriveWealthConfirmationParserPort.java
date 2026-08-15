package com.mx.cryptomonitor.statementimport.application.port.out;

import com.mx.cryptomonitor.statementimport.domain.model.ParsedConfirmationRow;

public interface DriveWealthConfirmationParserPort {

  ParsedConfirmationRow parse(byte[] pdfContent);
}
