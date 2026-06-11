package com.mx.cryptomonitor.asset.infrastructure.configuration;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class AssetConfig {

  @Bean
  @Qualifier("finnhubWebClient")
  public WebClient finnhubWebClient(
      WebClient.Builder webClientBuilder,
      @Value("${finnhub.base-url:https://finnhub.io/api/v1}") String baseUrl) {
    return webClientBuilder.baseUrl(baseUrl).build();
  }
}
