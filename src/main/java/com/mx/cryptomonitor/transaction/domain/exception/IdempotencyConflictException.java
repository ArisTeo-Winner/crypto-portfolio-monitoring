package com.mx.cryptomonitor.transaction.domain.exception;

public class IdempotencyConflictException extends RuntimeException {

  public IdempotencyConflictException(String message) {
    super(message);
  }
}
