package com.mx.cryptomonitor.statementimport.application.port.out;

import com.mx.cryptomonitor.statementimport.domain.model.StatementImportJobType;

/**
 * Detecta el broker/tipo concreto de un documento subido a partir de su contenido. Nunca devuelve
 * {@code AUTO_DETECT}: lanza {@code UnrecognizedBrokerDocumentException} si no reconoce el
 * documento.
 */
public interface BrokerDocumentDetectorPort {

  StatementImportJobType detect(byte[] content);
}
