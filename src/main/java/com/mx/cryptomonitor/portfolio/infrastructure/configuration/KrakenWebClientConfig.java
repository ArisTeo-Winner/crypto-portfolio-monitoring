package com.mx.cryptomonitor.portfolio.infrastructure.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
class KrakenWebClientConfig {

  @Bean(name = "krakenWebClient")
  WebClient krakenWebClient(
      WebClient.Builder builder,
      @Value("${external.providers.kraken.base-url:https://api.kraken.com}") String baseUrl) {
    // Kraken OHLC es publico (sin API key). Se fija User-Agent explicito por higiene de cliente
    // HTTP.
    return builder
        .baseUrl(baseUrl)
        .defaultHeader("User-Agent", "crypto-portfolio-monitoring")
        .build();
  }
}
