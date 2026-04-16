package com.mx.cryptomonitor.user.domain.exception;

public class TooManyMeDeleteRequestsException extends RuntimeException {

  private final long retryAfterSeconds;

  public TooManyMeDeleteRequestsException(long retryAfterSeconds) {
    super("Too many account deletion requests");
    this.retryAfterSeconds = Math.max(1L, retryAfterSeconds);
  }

  public long getRetryAfterSeconds() {
    return retryAfterSeconds;
  }
}
