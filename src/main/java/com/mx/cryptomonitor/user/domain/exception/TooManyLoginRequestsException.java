package com.mx.cryptomonitor.user.domain.exception;

public class TooManyLoginRequestsException extends RuntimeException {

  private final long retryAfterSeconds;

  public TooManyLoginRequestsException(long retryAfterSeconds) {
    super("Too many login attempts");
    this.retryAfterSeconds = retryAfterSeconds;
  }

  public long getRetryAfterSeconds() {
    return retryAfterSeconds;
  }
}
