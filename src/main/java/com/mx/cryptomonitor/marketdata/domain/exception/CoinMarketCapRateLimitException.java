package com.mx.cryptomonitor.marketdata.domain.exception;

public class CoinMarketCapRateLimitException extends CoinMarketCapException {
  public CoinMarketCapRateLimitException(String message) {
    super(message);
  }
}
