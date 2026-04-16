package com.mx.cryptomonitor.marketdata.domain.exception;

public class CoinGeckoServerException extends CoinGeckoException {

  public CoinGeckoServerException(String message) {
    super(message);
  }

  public CoinGeckoServerException(String message, Throwable cause) {
    super(message, cause);
  }
}
