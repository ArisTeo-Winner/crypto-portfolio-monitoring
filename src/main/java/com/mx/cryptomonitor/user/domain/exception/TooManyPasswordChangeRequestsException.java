package com.mx.cryptomonitor.user.domain.exception;

public class TooManyPasswordChangeRequestsException extends RuntimeException {

  private final long retryAfterSeconds;

  public TooManyPasswordChangeRequestsException(long retryAfterSeconds) {
    super("Too many password change attempts");
    this.retryAfterSeconds = Math.max(1L, retryAfterSeconds);
  }

  public long getRetryAfterSeconds() {
    return retryAfterSeconds;
  }
}
