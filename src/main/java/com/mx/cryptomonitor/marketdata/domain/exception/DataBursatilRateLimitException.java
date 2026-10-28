package com.mx.cryptomonitor.marketdata.domain.exception;

public class DataBursatilRateLimitException extends ExternalProviderRateLimitException {

  public DataBursatilRateLimitException(String message) {
    super("databursatil", message);
  }
}
