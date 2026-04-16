package com.mx.cryptomonitor.user.domain.exception;

public class SessionNotFoundException extends RuntimeException {
  public SessionNotFoundException(String message) {
    super(message);
  }
}
