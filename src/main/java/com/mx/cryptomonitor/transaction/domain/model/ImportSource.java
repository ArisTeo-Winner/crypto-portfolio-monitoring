package com.mx.cryptomonitor.transaction.domain.model;

/**
 * Procedencia de una transacción: alta manual o importación desde un comprobante/estado de broker.
 */
public enum ImportSource {
  MANUAL,
  DRIVEWEALTH,
  GBM_STATEMENT,
  GBM_EQUITY
}
