package com.mx.cryptomonitor.user.domain.exception;

public class TooManyRegistrationRequestsException extends RuntimeException {

  private final long retryAfterSeconds;

  public TooManyRegistrationRequestsException(long retryAfterSeconds) {
    super("Too many registration attempts");
    this.retryAfterSeconds = Math.max(1L, retryAfterSeconds);
  }

  public long getRetryAfterSeconds() {
    return retryAfterSeconds;
  }
}
