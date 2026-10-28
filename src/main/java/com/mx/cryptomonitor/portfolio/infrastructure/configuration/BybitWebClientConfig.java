package com.mx.cryptomonitor.portfolio.infrastructure.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
class BybitWebClientConfig {

  @Bean(name = "bybitWebClient")
  WebClient bybitWebClient(
      WebClient.Builder builder,
      @Value("${external.providers.bybit.base-url:https://api.bybit.com}") String baseUrl) {
    return builder.baseUrl(baseUrl).build();
  }
}
