package com.mx.cryptomonitor.statementimport.domain.exception;

public class InvalidStatementDocumentException extends RuntimeException {

  public InvalidStatementDocumentException(String message) {
    super(message);
  }

  public InvalidStatementDocumentException(String message, Throwable cause) {
    super(message, cause);
  }
}
