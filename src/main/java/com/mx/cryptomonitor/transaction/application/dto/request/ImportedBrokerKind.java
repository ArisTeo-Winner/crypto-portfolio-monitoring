package com.mx.cryptomonitor.transaction.application.dto.request;

/**
 * Discrimina el modelo de friccion a aplicar a una operacion importada. Determina que rama del
 * calculador de friccion usa el modulo transaction.
 */
public enum ImportedBrokerKind {
  /** DriveWealth/USD: comision + transaction/other fees reportados; sin IVA. */
  DRIVEWEALTH,
  /**
   * GBM casa de bolsa/MXN renta variable: comision 0.25% + IVA 16% (recalculados desde qty*precio).
   */
  GBM_MX_EQUITY
}
