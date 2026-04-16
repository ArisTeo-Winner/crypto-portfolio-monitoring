package com.mx.cryptomonitor.user.domain.exception;

public class TooManyPasswordResetRequestsException extends RuntimeException {

  private final long retryAfterSeconds;

  public TooManyPasswordResetRequestsException(long retryAfterSeconds) {
    super("Too many password reset attempts");
    this.retryAfterSeconds = Math.max(1L, retryAfterSeconds);
  }

  public long getRetryAfterSeconds() {
    return retryAfterSeconds;
  }
}
