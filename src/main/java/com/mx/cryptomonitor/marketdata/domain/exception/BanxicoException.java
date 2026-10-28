package com.mx.cryptomonitor.marketdata.domain.exception;

public class BanxicoException extends ExternalProviderUpstreamException {

  public BanxicoException(String message) {
    super("banxico", message);
  }

  public BanxicoException(String message, Throwable cause) {
    super("banxico", message, cause);
  }
}
