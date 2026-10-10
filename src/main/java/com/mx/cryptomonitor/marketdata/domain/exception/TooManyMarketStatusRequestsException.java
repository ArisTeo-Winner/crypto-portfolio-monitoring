package com.mx.cryptomonitor.marketdata.domain.exception;

public class TooManyMarketStatusRequestsException extends RuntimeException {

  private final long retryAfterSeconds;

  public TooManyMarketStatusRequestsException(long retryAfterSeconds) {
    super("Too many market status requests.");
    this.retryAfterSeconds = retryAfterSeconds;
  }

  public long getRetryAfterSeconds() {
    return retryAfterSeconds;
  }
}
