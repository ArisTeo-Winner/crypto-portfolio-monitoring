package com.mx.cryptomonitor.user.domain.exception;

public class TooManyEmailVerifyRequestsException extends RuntimeException {

  private final long retryAfterSeconds;

  public TooManyEmailVerifyRequestsException(long retryAfterSeconds) {
    super("Too many email verify attempts");
    this.retryAfterSeconds = Math.max(1L, retryAfterSeconds);
  }

  public long getRetryAfterSeconds() {
    return retryAfterSeconds;
  }
}
