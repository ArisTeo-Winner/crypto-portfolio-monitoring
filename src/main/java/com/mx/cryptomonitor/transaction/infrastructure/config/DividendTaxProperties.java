package com.mx.cryptomonitor.transaction.infrastructure.config;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import com.mx.cryptomonitor.transaction.domain.dividend.DividendTaxRates;

/**
 * Tasas de retencion de dividendos externalizadas (prefijo {@code dividend.tax}). {@code
 * sourceWithholdingRate} = retencion del pais de origen (US W-8BEN); {@code localWithholdingRate} =
 * retencion local (MX) sobre el neto despues de la de origen. Defaults = 10%/10%, de modo que la
 * ausencia de config no cambia el comportamiento.
 */
@ConfigurationProperties(prefix = "dividend.tax")
public record DividendTaxProperties(
    @DefaultValue("0.10") BigDecimal sourceWithholdingRate,
    @DefaultValue("0.10") BigDecimal localWithholdingRate) {

  public DividendTaxRates toRates() {
    return new DividendTaxRates(sourceWithholdingRate, localWithholdingRate);
  }
}
