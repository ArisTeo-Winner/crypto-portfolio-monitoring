package com.mx.cryptomonitor.portfolio.infrastructure.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
class BinanceFuturesWebClientConfig {

  @Bean(name = "binanceFuturesWebClient")
  WebClient binanceFuturesWebClient(
      WebClient.Builder builder,
      @Value("${external.providers.binance.futures.base-url:https://fapi.binance.com}")
          String baseUrl) {
    return builder.baseUrl(baseUrl).build();
  }
}
