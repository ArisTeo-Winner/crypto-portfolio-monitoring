package com.mx.cryptomonitor.statementimport.infrastructure.outbound.pdf;

import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Component;

import com.mx.cryptomonitor.statementimport.application.port.out.BrokerDocumentDetectorPort;
import com.mx.cryptomonitor.statementimport.domain.exception.UnrecognizedBrokerDocumentException;
import com.mx.cryptomonitor.statementimport.domain.model.StatementImportJobType;

import lombok.RequiredArgsConstructor;

/**
 * Detecta el tipo de documento por firmas de texto, en orden de mayor a menor especificidad:
 *
 * <ol>
 *   <li>Confirmacion DriveWealth: contiene "DriveWealth, LLC" (marca unica).
 *   <li>Comprobante de renta variable GBM: "Orden completada", o Emisora + Precio + Comision.
 *   <li>Estado de cuenta mensual GBM: "CASA DE BOLSA" / "DESGLOSE DE MOVIMIENTOS".
 * </ol>
 *
 * Los marcadores del comprobante son sensibles a mayusculas a proposito: el estado mensual usa
 * encabezados en MAYUSCULAS (EMISORA, COMISIONES), evitando falsos positivos con el comprobante.
 */
@Component
@RequiredArgsConstructor
public class PdfBrokerDocumentDetector implements BrokerDocumentDetectorPort {

  private final PdfBoxTextExtractor textExtractor;

  @Override
  public StatementImportJobType detect(byte[] content) {
    List<String> pages = textExtractor.extractPagesText(content);
    return detectText(String.join("\n", pages).replace("\r", ""));
  }

  StatementImportJobType detectText(String text) {
    if (text.contains("DriveWealth, LLC")) {
      return StatementImportJobType.DRIVEWEALTH_CONFIRMATION;
    }
    if (text.contains("Orden completada")
        || (text.contains("Emisora") && text.contains("Precio por t") && text.contains("Comisi"))) {
      return StatementImportJobType.GBM_EQUITY_CONFIRMATION;
    }
    String upper = text.toUpperCase(Locale.ROOT);
    if (upper.contains("CASA DE BOLSA") || upper.contains("DESGLOSE DE MOVIMIENTOS")) {
      return StatementImportJobType.GBM_STATEMENT;
    }
    throw new UnrecognizedBrokerDocumentException(
        "No se pudo detectar el broker/tipo del documento subido");
  }
}
