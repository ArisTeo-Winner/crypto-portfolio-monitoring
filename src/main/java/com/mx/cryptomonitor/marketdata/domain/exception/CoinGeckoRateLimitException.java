package com.mx.cryptomonitor.marketdata.domain.exception;

public class CoinGeckoRateLimitException extends CoinGeckoException {

  public CoinGeckoRateLimitException(String message) {
    super(message);
  }
}
