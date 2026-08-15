package com.mx.cryptomonitor.statementimport.infrastructure.outbound.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.mx.cryptomonitor.statementimport.domain.model.ParsedConfirmationRow;

class DriveWealthConfirmationParserTest {

  private final DriveWealthConfirmationParser parser =
      new DriveWealthConfirmationParser(new PdfBoxTextExtractor());

  @Test
  void parsesSyntheticSampleConfirmation() throws IOException {
    byte[] pdf = readResource("statementimport/drivewealth-confirmation-sample.pdf");

    ParsedConfirmationRow row = parser.parse(pdf);

    assertThat(row.symbol()).isEqualTo("SYNT");
    assertThat(row.securityName()).isEqualTo("SYNTHETIC TEST HOLDINGS INC COM");
    assertThat(row.action()).isEqualTo("Buy");
    assertThat(row.quantity()).isEqualByComparingTo(new BigDecimal("1"));
    assertThat(row.price()).isEqualByComparingTo(new BigDecimal("20.000000"));
    assertThat(row.tradeDate()).isEqualTo(LocalDate.of(2025, 6, 9));
    assertThat(row.commission()).isEqualByComparingTo(new BigDecimal("0.10"));
    assertThat(row.netAmount()).isEqualByComparingTo(new BigDecimal("20.10"));
  }

  private byte[] readResource(String resource) throws IOException {
    try (InputStream in =
        Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
      return in.readAllBytes();
    }
  }
}
