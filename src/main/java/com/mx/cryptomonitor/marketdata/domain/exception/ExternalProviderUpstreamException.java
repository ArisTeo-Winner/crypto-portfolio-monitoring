package com.mx.cryptomonitor.marketdata.domain.exception;

public class ExternalProviderUpstreamException extends RuntimeException {

  private final String providerName;

  public ExternalProviderUpstreamException(String providerName, String message) {
    super(message);
    this.providerName = providerName;
  }

  public ExternalProviderUpstreamException(String providerName, String message, Throwable cause) {
    super(message, cause);
    this.providerName = providerName;
  }

  public String getProviderName() {
    return providerName;
  }
}
