package com.mx.cryptomonitor.portfolio.infrastructure.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
class BinanceWebClientConfig {

  @Bean(name = "binanceWebClient")
  WebClient binanceWebClient(
      WebClient.Builder builder,
      @Value("${external.providers.binance.base-url:https://api.binance.com}") String baseUrl) {
    return builder.baseUrl(baseUrl).build();
  }
}
