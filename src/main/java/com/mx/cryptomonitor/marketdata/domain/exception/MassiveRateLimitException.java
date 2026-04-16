package com.mx.cryptomonitor.marketdata.domain.exception;

public class MassiveRateLimitException extends ExternalProviderRateLimitException {

  public MassiveRateLimitException(String message) {
    super("massive", message);
  }
}
