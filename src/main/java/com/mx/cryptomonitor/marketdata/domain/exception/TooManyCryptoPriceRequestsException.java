package com.mx.cryptomonitor.marketdata.domain.exception;

public class TooManyCryptoPriceRequestsException extends RuntimeException {

  private final long retryAfterSeconds;

  public TooManyCryptoPriceRequestsException(long retryAfterSeconds) {
    super("Too many crypto price requests.");
    this.retryAfterSeconds = retryAfterSeconds;
  }

  public long getRetryAfterSeconds() {
    return retryAfterSeconds;
  }
}
