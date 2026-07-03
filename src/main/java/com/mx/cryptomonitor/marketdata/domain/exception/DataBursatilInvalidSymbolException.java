package com.mx.cryptomonitor.marketdata.domain.exception;

public class DataBursatilInvalidSymbolException extends ExternalProviderInvalidSymbolException {

  public DataBursatilInvalidSymbolException(String message) {
    super("databursatil", message);
  }
}
