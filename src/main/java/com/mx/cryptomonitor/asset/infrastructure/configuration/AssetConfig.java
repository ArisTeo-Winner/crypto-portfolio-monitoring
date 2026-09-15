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

  /**
   * WebClient para resolver logos de CRYPTO vía CoinGecko /coins/markets (plan Demo: header {@code
   * x-cg-demo-api-key}). La key es opcional: sin ella el endpoint público responde igual, solo con
   * límites de tasa más estrictos.
   */
  @Bean
  @Qualifier("coinGeckoLogoWebClient")
  public WebClient coinGeckoLogoWebClient(
      WebClient.Builder webClientBuilder,
      @Value("${external.providers.coingecko.base-url:https://api.coingecko.com/api/v3}")
          String baseUrl,
      @Value("${external.providers.coingecko.api-key:}") String apiKey) {
    WebClient.Builder builder = webClientBuilder.baseUrl(baseUrl);
    if (apiKey != null && !apiKey.isBlank()) {
      builder = builder.defaultHeader("x-cg-demo-api-key", apiKey);
    }
    return builder.build();
  }
}
