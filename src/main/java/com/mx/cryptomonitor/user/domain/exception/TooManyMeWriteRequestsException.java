package com.mx.cryptomonitor.user.domain.exception;

public class TooManyMeWriteRequestsException extends RuntimeException {
  public TooManyMeWriteRequestsException() {
    super("Too many profile update requests");
  }
}
