package com.mx.cryptomonitor.marketdata.domain.exception;

public class AlphaVantageServerException extends ExternalProviderUpstreamException {

  public AlphaVantageServerException(String message) {
    super("alphavantage", message);
  }

  public AlphaVantageServerException(String message, Throwable cause) {
    super("alphavantage", message, cause);
  }
}
