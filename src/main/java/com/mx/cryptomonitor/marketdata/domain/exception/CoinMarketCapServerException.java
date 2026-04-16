package com.mx.cryptomonitor.marketdata.domain.exception;

public class CoinMarketCapServerException extends CoinMarketCapException {
  public CoinMarketCapServerException(String message) {
    super(message);
  }

  public CoinMarketCapServerException(String message, Throwable cause) {
    super(message, cause);
  }
}
