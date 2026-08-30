package com.mx.cryptomonitor.transaction.application.dto.request;

/**
 * Señal de procedencia a nivel de aplicación que cruza la frontera de módulos (statementimport →
 * transaction) para etiquetar el {@code ImportSource} de dominio de una transacción importada.
 */
public enum TransactionOrigin {
  MANUAL,
  DRIVEWEALTH,
  GBM_STATEMENT,
  GBM_EQUITY
}
