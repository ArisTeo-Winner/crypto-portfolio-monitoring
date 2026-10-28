package com.mx.cryptomonitor.statementimport.domain.model;

public enum StatementImportJobType {
  GBM_STATEMENT,
  DRIVEWEALTH_CONFIRMATION,
  GBM_EQUITY_CONFIRMATION,
  /** El worker detecta el broker/tipo real inspeccionando el contenido antes de despachar. */
  AUTO_DETECT
}
