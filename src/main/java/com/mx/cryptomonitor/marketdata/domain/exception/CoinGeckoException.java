package com.mx.cryptomonitor.marketdata.domain.exception;

public abstract class CoinGeckoException extends RuntimeException {

  protected CoinGeckoException(String message) {
    super(message);
  }

  protected CoinGeckoException(String message, Throwable cause) {
    super(message, cause);
  }
}
