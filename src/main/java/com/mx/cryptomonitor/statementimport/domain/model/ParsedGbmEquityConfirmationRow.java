package com.mx.cryptomonitor.statementimport.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Fila parseada de un comprobante de operacion de renta variable de GBM casa de bolsa (MXN), p. ej.
 * el ticket "Orden completada" de la app: emisora, titulos, precio por titulo, comision (0.25%),
 * IVA (16% sobre la comision) y monto total. Contrasta con {@link ParsedConfirmationRow}
 * (DriveWealth/USD, sin IVA) y con {@link ParsedStatementRow} (estado mensual consolidado).
 *
 * <p>Andamiaje: los patrones se validaron contra un ejemplo sintetico basado en la captura del
 * ticket FMTY 14; ajustar los regex cuando se disponga del PDF/comprobante real.
 */
public record ParsedGbmEquityConfirmationRow(
    String symbol,
    String action,
    BigDecimal quantity,
    BigDecimal unitPrice,
    BigDecimal commission,
    BigDecimal iva,
    BigDecimal totalAmount,
    String contrato,
    String folio,
    LocalDate tradeDate) {}
