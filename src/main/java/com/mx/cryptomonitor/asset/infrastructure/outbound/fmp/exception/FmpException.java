package com.mx.cryptomonitor.asset.infrastructure.outbound.fmp.exception;

import com.mx.cryptomonitor.asset.domain.exception.CatalogFetchException;

public class FmpException extends CatalogFetchException {

  public FmpException(String message) {
    super(message);
  }

  public FmpException(String message, Throwable cause) {
    super(message, cause);
  }
}
