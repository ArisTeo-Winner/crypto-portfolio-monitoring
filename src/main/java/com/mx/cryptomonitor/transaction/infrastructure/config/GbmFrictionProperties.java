package com.mx.cryptomonitor.transaction.infrastructure.config;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import com.mx.cryptomonitor.transaction.domain.friction.FrictionRates;

/**
 * Tasas de friccion GBM externalizadas (prefijo {@code friction.gbm}). Solo parametros de negocio
 * (comision/IVA/tolerancia); los patrones de parseo siguen en codigo. Con defaults = tasas
 * estandar, de modo que ausencia de config no cambia el comportamiento.
 */
@ConfigurationProperties(prefix = "friction.gbm")
public record GbmFrictionProperties(
    @DefaultValue("0.0025") BigDecimal commissionRate,
    @DefaultValue("0.16") BigDecimal ivaRate,
    @DefaultValue("0.05") BigDecimal tolerance) {

  public FrictionRates toRates() {
    return new FrictionRates(commissionRate, ivaRate, tolerance);
  }
}
