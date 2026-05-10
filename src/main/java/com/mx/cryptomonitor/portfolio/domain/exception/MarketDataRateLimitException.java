package com.mx.cryptomonitor.portfolio.domain.exception;

public class MarketDataRateLimitException extends RuntimeException {
  public MarketDataRateLimitException(String message) {
    super(message);
  }
}
