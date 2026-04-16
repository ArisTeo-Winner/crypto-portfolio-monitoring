package com.mx.cryptomonitor.transaction.domain.exception;

/** Excepción personalizada para manejar transacciones inválidas. */
public class InvalidTransactionException extends RuntimeException {
  /**
   * Constructor con mensaje personalizado.
   *
   * @param message Mensaje de error.
   */
  public InvalidTransactionException(String message) {
    super(message);
  }

  /**
   * Constructor con mensaje personalizado y causa.
   *
   * @param message Mensaje de error.
   * @param cause Causa de la excepción.
   */
  public InvalidTransactionException(String message, Throwable cause) {
    super(message, cause);
  }
}
