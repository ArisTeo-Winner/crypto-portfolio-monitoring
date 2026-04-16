package com.mx.cryptomonitor.marketdata.domain.exception;

public class AlphaVantageInvalidSymbolException extends ExternalProviderInvalidSymbolException {

  public AlphaVantageInvalidSymbolException(String message) {
    super("alphavantage", message);
  }
}
