package com.mx.cryptomonitor.statementimport.infrastructure.outbound.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.mx.cryptomonitor.statementimport.domain.model.GbmStatementParseResult;
import com.mx.cryptomonitor.statementimport.domain.model.ParsedStatementRow;
import com.mx.cryptomonitor.statementimport.domain.model.ParsedStatementRow.RowKind;

class GbmStatementParserTest {

  private final GbmStatementParser parser = new GbmStatementParser(new PdfBoxTextExtractor());

  @Test
  void parsesSyntheticSampleStatement() throws IOException {
    byte[] pdf = readResource("statementimport/gbm-statement-sample.pdf");

    GbmStatementParseResult result = parser.parse(pdf);

    assertThat(result.contractNumber()).isEqualTo("TEST99999");

    List<ParsedStatementRow> reportoRows =
        result.rows().stream().filter(r -> r.kind() == RowKind.REPORTO_SNAPSHOT).toList();
    assertThat(reportoRows).hasSize(1);
    ParsedStatementRow reporto = reportoRows.get(0);
    assertThat(reporto.emisora()).isEqualTo("ABC 261231");
    assertThat(reporto.quantity()).isEqualByComparingTo(new BigDecimal("2"));
    assertThat(reporto.pricePerUnit()).isEqualByComparingTo(new BigDecimal("95.500000"));
    assertThat(reporto.couponRate()).isEqualByComparingTo(new BigDecimal("9.00"));
    assertThat(reporto.maturityDate()).isEqualTo(LocalDate.of(2026, 12, 31));
    assertThat(reporto.operationDate()).isEqualTo(LocalDate.of(2025, 6, 30));

    List<ParsedStatementRow> forexBuys =
        result.rows().stream().filter(r -> r.kind() == RowKind.FOREX_BUY).toList();
    assertThat(forexBuys).hasSize(3);
    ParsedStatementRow firstForex = forexBuys.get(0);
    assertThat(firstForex.emisora()).isEqualTo("DOLARES");
    assertThat(firstForex.quantity()).isEqualByComparingTo(new BigDecimal("1"));
    assertThat(firstForex.pricePerUnit()).isEqualByComparingTo(new BigDecimal("19.500000"));
    assertThat(firstForex.operationDate()).isEqualTo(LocalDate.of(2025, 6, 9));

    long noiseRows = result.rows().stream().filter(r -> r.kind() == RowKind.NOISE).count();
    assertThat(noiseRows).isGreaterThan(0);

    long equityRows =
        result.rows().stream()
            .filter(r -> r.kind() == RowKind.EQUITY_BUY || r.kind() == RowKind.EQUITY_SELL)
            .count();
    assertThat(equityRows).isZero();
  }

  private byte[] readResource(String resource) throws IOException {
    try (InputStream in =
        Thread.currentThread().getContextClassLoader().getResourceAsStream(resource)) {
      return in.readAllBytes();
    }
  }
}
