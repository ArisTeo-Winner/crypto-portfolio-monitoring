package com.mx.cryptomonitor.statementimport.infrastructure.outbound.pdf;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import com.mx.cryptomonitor.statementimport.domain.model.ParsedGbmEquityConfirmationRow;

/**
 * Andamiaje: valida el parser GBM-MX contra un ejemplo sintetico basado en la captura del ticket
 * FMTY 14. Cuando exista un PDF real, agregar un test que lea el recurso real y ajustar los regex.
 */
class GbmEquityConfirmationParserTest {

  private final GbmEquityConfirmationParser parser =
      new GbmEquityConfirmationParser(new PdfBoxTextExtractor());

  private static final String SYNTHETIC_FMTY_TICKET =
      String.join(
          "\n",
          "Comprobante",
          "GBM",
          "Orden completada",
          "$1,554.49 MXN",
          "02 Jan 2026 – 09:47 h",
          "Emisora FMTY 14",
          "Tipo de operación Compra",
          "Tipo de orden Limitada",
          "Precio por título $15.50 MXN",
          "Títulos 100",
          "IVA 16% $0.620 MXN",
          "Comisión 0.25% $3.87 MXN",
          "Contrato CI43HI02",
          "Folio 102580702");

  @Test
  void parsesSyntheticFmtyTicketWithAllFields() {
    ParsedGbmEquityConfirmationRow row = parser.parseText(SYNTHETIC_FMTY_TICKET);

    assertThat(row.symbol()).isEqualTo("FMTY 14");
    assertThat(row.action()).isEqualTo("Compra");
    assertThat(row.quantity()).isEqualByComparingTo("100");
    assertThat(row.unitPrice()).isEqualByComparingTo("15.50");
    assertThat(row.commission()).isEqualByComparingTo("3.87");
    assertThat(row.iva()).isEqualByComparingTo("0.620");
    assertThat(row.totalAmount()).isEqualByComparingTo("1554.49");
    assertThat(row.contrato()).isEqualTo("CI43HI02");
    assertThat(row.folio()).isEqualTo("102580702");
    assertThat(row.tradeDate()).isEqualTo(LocalDate.of(2026, 1, 2));
  }

  @Test
  void frictionInvariantHolds() {
    ParsedGbmEquityConfirmationRow row = parser.parseText(SYNTHETIC_FMTY_TICKET);

    // Costo Bruto + Comision + IVA == Total (compra)
    BigDecimal gross = row.quantity().multiply(row.unitPrice());
    assertThat(gross.add(row.commission()).add(row.iva())).isEqualByComparingTo(row.totalAmount());
  }
}
