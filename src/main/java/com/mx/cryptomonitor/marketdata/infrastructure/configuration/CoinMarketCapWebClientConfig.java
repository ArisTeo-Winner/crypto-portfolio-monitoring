package com.mx.cryptomonitor.marketdata.infrastructure.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@EnableConfigurationProperties(CoinMarketCapProperties.class)
public class CoinMarketCapWebClientConfig {

  @Value("${webclient.maxInMemorySize:5MB}")
  private String maxInMemory;

  @Bean(name = "coinMarketCapWebClient")
  public WebClient coinMarketCapWebClient(
      WebClient.Builder builder, CoinMarketCapProperties coinMarketCapProperties) {

    int maxMemorySize = Integer.parseInt(maxInMemory.replaceAll("\\D+", "")) * 1024 * 1024;

    WebClient.Builder configuredBuilder =
        builder.exchangeStrategies(
            ExchangeStrategies.builder()
                .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(maxMemorySize))
                .build());

    if (StringUtils.hasText(coinMarketCapProperties.baseUrl())) {
      configuredBuilder = configuredBuilder.baseUrl(coinMarketCapProperties.baseUrl());
    }

    if (StringUtils.hasText(coinMarketCapProperties.apiKey())) {
      configuredBuilder =
          configuredBuilder.defaultHeader("X-CMC_PRO_API_KEY", coinMarketCapProperties.apiKey());
    }

    return configuredBuilder.build();
  }
}
