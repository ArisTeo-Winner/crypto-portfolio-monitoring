package com.mx.cryptomonitor.user.domain.exception;

public class TooManyMeReadRequestsException extends RuntimeException {
  public TooManyMeReadRequestsException() {
    super("Too many profile read requests");
  }
}
