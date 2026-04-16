package com.mx.cryptomonitor.marketdata.domain.exception;

public class ExternalProviderInvalidSymbolException extends RuntimeException {

  private final String providerName;

  public ExternalProviderInvalidSymbolException(String providerName, String message) {
    super(message);
    this.providerName = providerName;
  }

  public String getProviderName() {
    return providerName;
  }
}
