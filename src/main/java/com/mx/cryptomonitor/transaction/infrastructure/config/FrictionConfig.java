package com.mx.cryptomonitor.transaction.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.mx.cryptomonitor.transaction.domain.friction.GbmFrictionCalculator;

@Configuration
@EnableConfigurationProperties(GbmFrictionProperties.class)
public class FrictionConfig {

  @Bean
  public GbmFrictionCalculator gbmFrictionCalculator(GbmFrictionProperties properties) {
    return new GbmFrictionCalculator(properties.toRates());
  }
}
