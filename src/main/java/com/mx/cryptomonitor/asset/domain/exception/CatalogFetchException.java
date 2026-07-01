package com.mx.cryptomonitor.asset.domain.exception;

public class CatalogFetchException extends RuntimeException {

  public CatalogFetchException(String message) {
    super(message);
  }

  public CatalogFetchException(String message, Throwable cause) {
    super(message, cause);
  }
}
