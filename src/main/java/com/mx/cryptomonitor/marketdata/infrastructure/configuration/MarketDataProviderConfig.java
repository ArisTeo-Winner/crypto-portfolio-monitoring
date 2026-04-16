package com.mx.cryptomonitor.marketdata.infrastructure.configuration;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.reactive.function.client.WebClient;

import com.mx.cryptomonitor.marketdata.application.port.out.MarketDataProvider;
import com.mx.cryptomonitor.marketdata.application.port.out.StockQuoteProvider;
import com.mx.cryptomonitor.marketdata.application.service.StockQuoteOrchestrator;
import com.mx.cryptomonitor.marketdata.infrastructure.outbound.alphavantage.AlphaVantageAdapter;
import com.mx.cryptomonitor.marketdata.infrastructure.outbound.massive.MassiveAdapter;

@Configuration
@EnableConfigurationProperties({AlphaVantageProperties.class, MassiveProperties.class})
public class MarketDataProviderConfig {

  @Bean
  public StockQuoteProvider alphaVantageStockQuoteProvider(
      WebClient webClient, AlphaVantageProperties alphaVantageProperties) {
    return new AlphaVantageAdapter(
        webClient, alphaVantageProperties.getBaseUrl(), alphaVantageProperties.getApiKey());
  }

  @Bean
  @ConditionalOnExpression(
      "'${marketdata.massive.base-url:}' != '' and '${marketdata.massive.api-key:}' != ''")
  public StockQuoteProvider massiveStockQuoteProvider(
      WebClient webClient, MassiveProperties massiveProperties) {
    return new MassiveAdapter(
        webClient, massiveProperties.getBaseUrl(), massiveProperties.getApiKey());
  }

  @Bean
  @Primary
  public MarketDataProvider stockQuoteOrchestrator(
      @Qualifier("alphaVantageStockQuoteProvider")
          StockQuoteProvider alphaVantageStockQuoteProvider,
      @Qualifier("massiveStockQuoteProvider")
          ObjectProvider<StockQuoteProvider> massiveStockQuoteProvider) {
    List<StockQuoteProvider> providers = new ArrayList<>();
    providers.add(alphaVantageStockQuoteProvider);
    massiveStockQuoteProvider.ifAvailable(providers::add);
    return new StockQuoteOrchestrator(providers);
  }
}
