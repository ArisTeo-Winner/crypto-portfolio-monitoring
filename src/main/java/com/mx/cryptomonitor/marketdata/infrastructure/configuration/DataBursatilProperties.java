package com.mx.cryptomonitor.marketdata.infrastructure.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "databursatil")
public record DataBursatilProperties(String baseUrl, String token) {}
