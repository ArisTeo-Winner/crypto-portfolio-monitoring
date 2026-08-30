package com.mx.cryptomonitor.statementimport.infrastructure.outbound.pdf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.mx.cryptomonitor.statementimport.domain.exception.UnrecognizedBrokerDocumentException;
import com.mx.cryptomonitor.statementimport.domain.model.StatementImportJobType;

class PdfBrokerDocumentDetectorTest {

  private final PdfBrokerDocumentDetector detector =
      new PdfBrokerDocumentDetector(new PdfBoxTextExtractor());

  @Test
  void detectsDriveWealthConfirmationByUniqueMarker() {
    String text =
        String.join(
            "\n",
            "Transaction GBM",
            "Account Number:GBMP-001-REDACTED",
            "CRCL CIRCLE STAR ENERGY CORP COM M Buy 9:32:25 AM 0.95368698 108.7988 6/11/2025"
                + " 6/12/2025 Principal",
            "Net Amount $104.01",
            "Clearing and execution services provided by DriveWealth, LLC member FINRA and SIPC.");

    assertThat(detector.detectText(text))
        .isEqualTo(StatementImportJobType.DRIVEWEALTH_CONFIRMATION);
  }

  @Test
  void detectsGbmEquityConfirmationByOrdenCompletada() {
    String text =
        String.join(
            "\n",
            "GBM",
            "Orden completada",
            "$1,554.49 MXN",
            "Emisora FMTY 14",
            "Precio por título $15.50 MXN",
            "Comisión 0.25% $3.87 MXN");

    assertThat(detector.detectText(text)).isEqualTo(StatementImportJobType.GBM_EQUITY_CONFIRMATION);
  }

  @Test
  void detectsGbmMonthlyStatementByCasaDeBolsa() {
    String text =
        String.join(
            "\n",
            "GRUPO BURSATIL MEXICANO, S.A. DE C.V. CASA DE BOLSA",
            "DESGLOSE DE MOVIMIENTOS",
            "EMISORA NUMERO TITULOS PRECIO UNITARIO COMISION IMPUESTO NETO");

    assertThat(detector.detectText(text)).isEqualTo(StatementImportJobType.GBM_STATEMENT);
  }

  @Test
  void throwsWhenDocumentIsNotRecognized() {
    assertThatThrownBy(() -> detector.detectText("Un recibo cualquiera sin marcas de broker"))
        .isInstanceOf(UnrecognizedBrokerDocumentException.class);
  }
}
