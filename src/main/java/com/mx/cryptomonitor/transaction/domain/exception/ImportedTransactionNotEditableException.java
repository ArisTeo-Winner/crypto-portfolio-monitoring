package com.mx.cryptomonitor.transaction.domain.exception;

/**
 * Se lanza al intentar modificar campos financieros de una transaccion importada (DriveWealth/GBM).
 * Un importado es el registro autoritativo del broker: solo las notas son editables; cambiar
 * cantidad/precio/fecha/fee romperia la conciliacion con el estado de cuenta.
 */
public class ImportedTransactionNotEditableException extends RuntimeException {
  public ImportedTransactionNotEditableException(String message) {
    super(message);
  }
}
