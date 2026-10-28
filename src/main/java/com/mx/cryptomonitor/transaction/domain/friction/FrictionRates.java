package com.mx.cryptomonitor.transaction.domain.friction;

import java.math.BigDecimal;

/**
 * Parametros de negocio de la friccion de corretaje GBM (MXN renta variable): tasa de comision,
 * tasa de IVA y tolerancia de reconciliacion. Value object inmutable; se externaliza desde
 * configuracion (no los regex de parseo, que siguen en codigo).
 */
public record FrictionRates(BigDecimal commissionRate, BigDecimal ivaRate, BigDecimal tolerance) {

  public static FrictionRates standard() {
    return new FrictionRates(
        new BigDecimal("0.0025"), new BigDecimal("0.16"), new BigDecimal("0.05"));
  }
}
