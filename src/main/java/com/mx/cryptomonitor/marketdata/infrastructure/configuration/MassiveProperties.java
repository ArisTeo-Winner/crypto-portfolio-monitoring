package com.mx.cryptomonitor.marketdata.infrastructure.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

@Data
@ConfigurationProperties(prefix = "marketdata.massive")
public class MassiveProperties {

  private String baseUrl;
  private String apiKey;
}
