package com.mx.cryptomonitor.asset.domain.exception;

public class TooManyAssetSearchRequestsException extends RuntimeException {

  private final long retryAfterSeconds;

  public TooManyAssetSearchRequestsException(long retryAfterSeconds) {
    super("Too many asset search requests.");
    this.retryAfterSeconds = retryAfterSeconds;
  }

  public long getRetryAfterSeconds() {
    return retryAfterSeconds;
  }
}
