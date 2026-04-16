package com.mx.cryptomonitor.shared.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

  @Value("${webclient.maxInMemorySize:5MB}")
  private String maxInMemory;

  @Primary
  @Bean
  public WebClient webClient(WebClient.Builder builder) {
    return builder
        .exchangeStrategies(
            ExchangeStrategies.builder()
                .codecs(
                    configurer ->
                        configurer
                            .defaultCodecs()
                            .maxInMemorySize(
                                Integer.parseInt(maxInMemory.replaceAll("\\D+", "")) * 1024 * 1024))
                .build())
        .build();
  }
}
