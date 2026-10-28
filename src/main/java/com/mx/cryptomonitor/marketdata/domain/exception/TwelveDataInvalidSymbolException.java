package com.mx.cryptomonitor.marketdata.domain.exception;

public class TwelveDataInvalidSymbolException extends ExternalProviderInvalidSymbolException {

  public TwelveDataInvalidSymbolException(String message) {
    super("twelvedata", message);
  }
}
