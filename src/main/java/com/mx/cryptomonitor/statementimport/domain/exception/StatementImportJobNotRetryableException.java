package com.mx.cryptomonitor.statementimport.domain.exception;

public class StatementImportJobNotRetryableException extends RuntimeException {

  public StatementImportJobNotRetryableException(String message) {
    super(message);
  }
}
