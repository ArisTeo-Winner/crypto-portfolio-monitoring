package com.mx.cryptomonitor.portfolio.infrastructure.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
class CoinbaseWebClientConfig {

  @Bean(name = "coinbaseWebClient")
  WebClient coinbaseWebClient(
      WebClient.Builder builder,
      @Value("${external.providers.coinbase.base-url:https://api.coinbase.com}") String baseUrl) {
    // Coinbase rechaza peticiones sin User-Agent; se fija uno explicito. El JWT ES256 (ADR-0023) se
    // agrega por peticion en el adapter, no aqui, porque va ligado al metodo+ruta.
    return builder
        .baseUrl(baseUrl)
        .defaultHeader("User-Agent", "crypto-portfolio-monitoring")
        .build();
  }
}
