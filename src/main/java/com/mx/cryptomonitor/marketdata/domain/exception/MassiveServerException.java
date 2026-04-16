package com.mx.cryptomonitor.marketdata.domain.exception;

public class MassiveServerException extends ExternalProviderUpstreamException {

  public MassiveServerException(String message) {
    super("massive", message);
  }

  public MassiveServerException(String message, Throwable cause) {
    super("massive", message, cause);
  }
}
