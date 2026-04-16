package com.mx.cryptomonitor.marketdata.infrastructure.configuration;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "external.providers.coingecko")
public record CoinGeckoProperties(
    String baseUrl, String apiKey, Duration responseTimeout, Duration cacheTtl, boolean enabled) {}
