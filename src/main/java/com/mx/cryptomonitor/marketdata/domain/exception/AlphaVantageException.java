package com.mx.cryptomonitor.marketdata.domain.exception;

public abstract class AlphaVantageException extends RuntimeException {

  protected AlphaVantageException(String message) {
    super(message);
  }

  protected AlphaVantageException(String message, Throwable cause) {
    super(message, cause);
  }
}
