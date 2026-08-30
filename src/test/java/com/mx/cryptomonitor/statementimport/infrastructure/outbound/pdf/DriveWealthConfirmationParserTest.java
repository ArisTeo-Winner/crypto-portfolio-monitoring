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
    assertThat(row.principalAmount()).isEqualByComparingTo(new BigDecimal("20.00"));
    assertThat(row.transactionFee()).isEqualByComparingTo(new BigDecimal("0.00"));
    assertThat(row.otherFees()).isEqualByComparingTo(new BigDecimal("0.00"));
  }

  @Test
  void parsesRealCrclConfirmationTextWithFullFeeBreakdown() {
    // Texto real de la confirmacion GBMP/DriveWealth (CRCL) con el numero de cuenta y nombre
    // redactados; los montos son los del ticket verdadero.
    String text =
        String.join(
            "\n",
            "Transaction GBM",
            "Confirmation",
            "Account Number:GBMP-001-REDACTED Account Name: TEST USER",
            "Confirmation Date : 6/11/2025",
            "Symbol Security A/CType Action Execution Time Quantity Price Trade Date Settle Date"
                + " Capacity",
            "CRCL CIRCLE STAR ENERGY CORP COM M Buy 9:32:25 AM 0.95368698 108.7988 6/11/2025"
                + " 6/12/2025 Principal",
            "Principal Amount $103.76",
            "Interest",
            "Commission $0.25",
            "Transaction Fee $0.00",
            "Other Fees / Credits $0.00",
            "Net Amount $104.01",
            "Clearing and execution services provided by DriveWealth, LLC member FINRA and SIPC.");

    ParsedConfirmationRow row = parser.parseText(text);

    assertThat(row.symbol()).isEqualTo("CRCL");
    assertThat(row.securityName()).isEqualTo("CIRCLE STAR ENERGY CORP COM");
    assertThat(row.action()).isEqualTo("Buy");
    assertThat(row.quantity()).isEqualByComparingTo(new BigDecimal("0.95368698"));
    assertThat(row.price()).isEqualByComparingTo(new BigDecimal("108.7988"));
    assertThat(row.tradeDate()).isEqualTo(LocalDate.of(2025, 6, 11));
    assertThat(row.principalAmount()).isEqualByComparingTo(new BigDecimal("103.76"));
    assertThat(row.commission()).isEqualByComparingTo(new BigDecimal("0.25"));
    assertThat(row.transactionFee()).isEqualByComparingTo(new BigDecimal("0.00"));
    assertThat(row.otherFees()).isEqualByComparingTo(new BigDecimal("0.00"));
    assertThat(row.netAmount()).isEqualByComparingTo(new BigDecimal("104.01"));

    // Invariante: Net - Principal == Commission + Transaction Fee + Other Fees
    assertThat(row.netAmount().subtract(row.principalAmount()))
        .isEqualByComparingTo(row.commission().add(row.transactionFee()).add(row.otherFees()));
  }

  private byte[] readResource(String resource) throws IOException {
    try (InputStream in =
        Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
      return in.readAllBytes();
    }
  }
}
