package com.mx.cryptomonitor.portfolio.domain.exception;

public class UnknownAssetSymbolException extends RuntimeException {
  public UnknownAssetSymbolException(String message) {
    super(message);
  }
}
