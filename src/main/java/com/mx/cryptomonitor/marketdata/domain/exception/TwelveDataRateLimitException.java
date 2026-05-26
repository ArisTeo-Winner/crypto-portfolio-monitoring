package com.mx.cryptomonitor.marketdata.domain.exception;

public class TwelveDataRateLimitException extends ExternalProviderRateLimitException {

  public TwelveDataRateLimitException(String message) {
    super("twelvedata", message);
  }
}
