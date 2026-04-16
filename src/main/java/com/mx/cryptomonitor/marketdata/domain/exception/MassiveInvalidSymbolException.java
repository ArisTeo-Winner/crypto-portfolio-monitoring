package com.mx.cryptomonitor.marketdata.domain.exception;

public class MassiveInvalidSymbolException extends ExternalProviderInvalidSymbolException {

  public MassiveInvalidSymbolException(String message) {
    super("massive", message);
  }
}
