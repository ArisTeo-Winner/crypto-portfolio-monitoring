package com.mx.cryptomonitor.statementimport.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Una fila normalizada del estado de cuenta GBM, ya sea de la tabla "DESGLOSE DE MOVIMIENTOS" o de
 * la foto de cierre "DEUDA EN REPORTO". {@code couponRate} y {@code maturityDate} solo aplican a
 * {@link RowKind#REPORTO_SNAPSHOT}.
 */
public record ParsedStatementRow(
    RowKind kind,
    String folio,
    LocalDate operationDate,
    String description,
    String emisora,
    BigDecimal quantity,
    BigDecimal pricePerUnit,
    BigDecimal commission,
    BigDecimal couponRate,
    LocalDate maturityDate) {

  public enum RowKind {
    EQUITY_BUY,
    EQUITY_SELL,
    FOREX_BUY,
    FOREX_SELL,
    REPORTO_SNAPSHOT,
    NOISE
  }
}
