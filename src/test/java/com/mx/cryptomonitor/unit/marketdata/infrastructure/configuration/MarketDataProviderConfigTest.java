package com.mx.cryptomonitor.unit.marketdata.infrastructure.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.reactive.function.client.WebClient;

import com.mx.cryptomonitor.marketdata.application.port.out.MarketDataProvider;
import com.mx.cryptomonitor.marketdata.application.port.out.StockQuoteProvider;
import com.mx.cryptomonitor.marketdata.application.service.StockQuoteOrchestrator;
import com.mx.cryptomonitor.marketdata.infrastructure.configuration.AlphaVantageProperties;
import com.mx.cryptomonitor.marketdata.infrastructure.configuration.MarketDataProviderConfig;
import com.mx.cryptomonitor.marketdata.infrastructure.configuration.MassiveProperties;
import com.mx.cryptomonitor.marketdata.infrastructure.outbound.alphavantage.AlphaVantageAdapter;
import com.mx.cryptomonitor.marketdata.infrastructure.outbound.massive.MassiveAdapter;

@ExtendWith(MockitoExtension.class)
class MarketDataProviderConfigTest {

  @Mock private ObjectProvider<StockQuoteProvider> massiveProvider;

  @Test
  void shouldExposeAlphaVantageAsStockQuoteProviderOnly() {
    MarketDataProviderConfig config = new MarketDataProviderConfig();
    AlphaVantageProperties properties = new AlphaVantageProperties();
    properties.setBaseUrl("https://www.alphavantage.co");
    properties.setApiKey("demo-key");

    StockQuoteProvider provider =
        config.alphaVantageStockQuoteProvider(WebClient.builder().build(), properties);

    assertThat(provider).isInstanceOf(AlphaVantageAdapter.class);
    assertThat(provider).isNotInstanceOf(StockQuoteOrchestrator.class);
  }

  @Test
  void shouldExposeMassiveAsOptionalStockQuoteProvider() {
    MarketDataProviderConfig config = new MarketDataProviderConfig();
    MassiveProperties properties = new MassiveProperties();
    properties.setBaseUrl("https://api.massive.com");
    properties.setApiKey("demo-key");

    StockQuoteProvider provider =
        config.massiveStockQuoteProvider(WebClient.builder().build(), properties);

    assertThat(provider).isInstanceOf(MassiveAdapter.class);
    assertThat(provider).isNotInstanceOf(StockQuoteOrchestrator.class);
  }

  @Test
  void shouldExposeOrchestratorAsPublicMarketDataProvider() {
    MarketDataProviderConfig config = new MarketDataProviderConfig();
    StockQuoteProvider alphaProvider =
        new AlphaVantageAdapter(
            WebClient.builder().build(), "https://www.alphavantage.co", "demo-key");
    StockQuoteProvider massiveAdapter =
        new MassiveAdapter(WebClient.builder().build(), "https://api.massive.com", "demo-key");

    doAnswer(
            invocation -> {
              invocation
                  .<java.util.function.Consumer<StockQuoteProvider>>getArgument(0)
                  .accept(massiveAdapter);
              return null;
            })
        .when(massiveProvider)
        .ifAvailable(any());

    MarketDataProvider marketDataProvider =
        config.stockQuoteOrchestrator(alphaProvider, massiveProvider);

    assertThat(marketDataProvider).isInstanceOf(StockQuoteOrchestrator.class);
  }
}
