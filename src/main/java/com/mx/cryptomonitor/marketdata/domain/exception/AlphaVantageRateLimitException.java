package com.mx.cryptomonitor.marketdata.domain.exception;

public class AlphaVantageRateLimitException extends ExternalProviderRateLimitException {

  public AlphaVantageRateLimitException(String message) {
    super("alphavantage", message);
  }
}
