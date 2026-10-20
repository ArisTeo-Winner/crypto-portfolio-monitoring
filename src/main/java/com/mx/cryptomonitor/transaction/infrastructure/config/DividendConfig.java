package com.mx.cryptomonitor.transaction.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.mx.cryptomonitor.transaction.domain.dividend.DividendEntitlementCalculator;
import com.mx.cryptomonitor.transaction.domain.dividend.DividendTaxCalculator;

/** Wiring del motor de dividendos (ADR-0014 F3c): cadena fiscal + entitlement. */
@Configuration
@EnableConfigurationProperties(DividendTaxProperties.class)
public class DividendConfig {

  @Bean
  public DividendTaxCalculator dividendTaxCalculator(DividendTaxProperties properties) {
    return new DividendTaxCalculator(properties.toRates());
  }

  @Bean
  public DividendEntitlementCalculator dividendEntitlementCalculator() {
    return new DividendEntitlementCalculator();
  }
}
