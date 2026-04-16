package com.mx.cryptomonitor.marketdata.domain.exception;

public class ExternalProviderRateLimitException extends RuntimeException {

  private final String providerName;
  private final Long retryAfterSeconds;

  public ExternalProviderRateLimitException(String providerName, String message) {
    this(providerName, message, null);
  }

  public ExternalProviderRateLimitException(
      String providerName, String message, Long retryAfterSeconds) {
    super(message);
    this.providerName = providerName;
    this.retryAfterSeconds = retryAfterSeconds;
  }

  public String getProviderName() {
    return providerName;
  }

  public Long getRetryAfterSeconds() {
    return retryAfterSeconds;
  }
}
