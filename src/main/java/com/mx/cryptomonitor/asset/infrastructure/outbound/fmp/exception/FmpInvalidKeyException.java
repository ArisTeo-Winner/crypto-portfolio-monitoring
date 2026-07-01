package com.mx.cryptomonitor.asset.infrastructure.outbound.fmp.exception;

import com.mx.cryptomonitor.asset.domain.exception.CatalogKeyInvalidException;

public class FmpInvalidKeyException extends CatalogKeyInvalidException {

  public FmpInvalidKeyException(String message) {
    super(message);
  }
}
