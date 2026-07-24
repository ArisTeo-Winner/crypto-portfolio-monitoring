package com.mx.cryptomonitor.marketdata.domain.exception;

public class BanxicoRateLimitException extends ExternalProviderRateLimitException {

  public BanxicoRateLimitException(String message) {
    super("banxico", message);
  }
}
