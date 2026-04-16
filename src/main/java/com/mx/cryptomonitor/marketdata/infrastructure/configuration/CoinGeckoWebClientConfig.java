package com.mx.cryptomonitor.marketdata.infrastructure.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties(CoinGeckoProperties.class)
public class CoinGeckoWebClientConfig {

  @Value("${webclient.maxInMemorySize:5MB}")
  private String maxInMemory;

  @Bean(name = "coinGeckoWebClient")
  public WebClient coinGeckoWebClient(
      WebClient.Builder builder, CoinGeckoProperties coinGeckoProperties) {

    int maxMemorySize = Integer.parseInt(maxInMemory.replaceAll("\\D+", "")) * 1024 * 1024;

    WebClient.Builder configuredBuilder =
        builder.exchangeStrategies(
            ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(maxMemorySize))
                .build());

    if (StringUtils.hasText(coinGeckoProperties.baseUrl())) {
      configuredBuilder = configuredBuilder.baseUrl(coinGeckoProperties.baseUrl());
    }

    if (StringUtils.hasText(coinGeckoProperties.apiKey())) {
      configuredBuilder =
          configuredBuilder.defaultHeader("x-cg-pro-api-key", coinGeckoProperties.apiKey());
    }

    return configuredBuilder.build();
  }
}
