package com.mx.cryptomonitor.marketdata.domain.exception;

public abstract class CoinMarketCapException extends RuntimeException {
  protected CoinMarketCapException(String message) {
    super(message);
  }

  protected CoinMarketCapException(String message, Throwable cause) {
    super(message, cause);
  }
}
