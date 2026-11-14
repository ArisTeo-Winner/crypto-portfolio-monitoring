package com.mx.cryptomonitor.marketdata.infrastructure.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/** Config de EODHD (ADR-0016): proveedor de precio de hoy multi-mercado por sufijo. */
@Data
@ConfigurationProperties(prefix = "eodhd")
public class EodhdProperties {

  private String baseUrl;
  private String apiToken;
  private String defaultSuffix = ".US";
}
