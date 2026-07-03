package com.mx.cryptomonitor.marketdata.domain.exception;

public class DataBursatilException extends ExternalProviderUpstreamException {

  public DataBursatilException(String message) {
    super("databursatil", message);
  }

  public DataBursatilException(String message, Throwable cause) {
    super("databursatil", message, cause);
  }
}
