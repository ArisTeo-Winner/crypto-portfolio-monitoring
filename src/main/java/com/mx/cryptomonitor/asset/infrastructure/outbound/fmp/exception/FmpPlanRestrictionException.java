package com.mx.cryptomonitor.asset.infrastructure.outbound.fmp.exception;

import com.mx.cryptomonitor.asset.domain.exception.CatalogPlanRestrictedException;

public class FmpPlanRestrictionException extends CatalogPlanRestrictedException {

  public FmpPlanRestrictionException(String message) {
    super(message);
  }
}
