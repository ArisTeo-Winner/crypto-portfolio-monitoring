package com.mx.cryptomonitor.marketdata.infrastructure.configuration;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "external.providers.coinmarketcap")
public record CoinMarketCapProperties(
    String baseUrl,
    String apiKey,
    Duration responseTimeout,
    Duration cacheTtl,
    String defaultConvert,
    boolean enabled) {}
