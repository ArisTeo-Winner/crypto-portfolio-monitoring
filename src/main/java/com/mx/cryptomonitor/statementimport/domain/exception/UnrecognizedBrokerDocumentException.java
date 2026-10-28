package com.mx.cryptomonitor.statementimport.domain.exception;

public class UnrecognizedBrokerDocumentException extends RuntimeException {

  public UnrecognizedBrokerDocumentException(String message) {
    super(message);
  }
}
