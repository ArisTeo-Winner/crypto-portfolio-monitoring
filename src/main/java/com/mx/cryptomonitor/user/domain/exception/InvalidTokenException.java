package com.mx.cryptomonitor.user.domain.exception;

public class InvalidTokenException extends RuntimeException {
  public InvalidTokenException(String message) {
    super(message);
  }
}
