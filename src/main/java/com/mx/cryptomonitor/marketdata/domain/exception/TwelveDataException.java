package com.mx.cryptomonitor.marketdata.domain.exception;

public class TwelveDataException extends ExternalProviderUpstreamException {

  public TwelveDataException(String message) {
    super("twelvedata", message);
  }

  public TwelveDataException(String message, Throwable cause) {
    super("twelvedata", message, cause);
  }
}
