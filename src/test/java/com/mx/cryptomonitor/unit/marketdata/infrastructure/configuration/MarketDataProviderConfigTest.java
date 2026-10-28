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
import com.mx.cryptomonitor.marketdata.application.port.out.StockTimeSeriesPort;
import com.mx.cryptomonitor.marketdata.application.service.StockQuoteOrchestrator;
import com.mx.cryptomonitor.marketdata.infrastructure.configuration.AlphaVantageProperties;
import com.mx.cryptomonitor.marketdata.infrastructure.configuration.MarketDataProviderConfig;
import com.mx.cryptomonitor.marketdata.infrastructure.configuration.MassiveProperties;
import com.mx.cryptomonitor.marketdata.infrastructure.configuration.TwelveDataProperties;
import com.mx.cryptomonitor.marketdata.infrastructure.outbound.alphavantage.AlphaVantageAdapter;
import com.mx.cryptomonitor.marketdata.infrastructure.outbound.massive.MassiveAdapter;
import com.mx.cryptomonitor.marketdata.infrastructure.outbound.twelvedata.TwelveDataAdapter;

@ExtendWith(MockitoExtension.class)
class MarketDataProviderConfigTest {

  @Mock private ObjectProvider<TwelveDataAdapter> twelveDataAdapterProvider;
  @Mock private ObjectProvider<StockQuoteProvider> massiveProvider;

  @Test
  void shouldExposeUniqueTwelveDataAdapterImplementingBothInterfaces() {
    MarketDataProviderConfig config = new MarketDataProviderConfig();
    TwelveDataProperties properties = new TwelveDataProperties();
    properties.setBaseUrl("https://api.twelvedata.com");
    properties.setApiKey("demo-key");

    TwelveDataAdapter adapter = config.twelveDataAdapter(WebClient.builder().build(), properties);

    // Un solo bean implementa ambas interfaces — sin duplicados
    assertThat(adapter).isInstanceOf(StockQuoteProvider.class);
    assertThat(adapter).isInstanceOf(StockTimeSeriesPort.class);
    assertThat(adapter).isNotInstanceOf(StockQuoteOrchestrator.class);
  }

  @Test
  void shouldExposeAlphaVantageAsStockQuoteProvider() {
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
  void shouldExposeOrchestratorWithTwelveDataFirstWhenConfigured() {
    MarketDataProviderConfig config = new MarketDataProviderConfig();
    TwelveDataAdapter twelveDataAdapter =
        new TwelveDataAdapter(
            WebClient.builder().build(), "https://api.twelvedata.com", "demo-key");
    StockQuoteProvider alphaProvider =
        new AlphaVantageAdapter(
            WebClient.builder().build(), "https://www.alphavantage.co", "demo-key");
    StockQuoteProvider massiveAdapter =
        new MassiveAdapter(WebClient.builder().build(), "https://api.massive.com", "demo-key");

    doAnswer(
            invocation -> {
              invocation
                  .<java.util.function.Consumer<TwelveDataAdapter>>getArgument(0)
                  .accept(twelveDataAdapter);
              return null;
            })
        .when(twelveDataAdapterProvider)
        .ifAvailable(any());

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
        config.stockQuoteOrchestrator(twelveDataAdapterProvider, alphaProvider, massiveProvider);

    assertThat(marketDataProvider).isInstanceOf(StockQuoteOrchestrator.class);
  }

  @Test
  void shouldExposeOrchestratorWithoutTwelveDataWhenNotConfigured() {
    MarketDataProviderConfig config = new MarketDataProviderConfig();
    StockQuoteProvider alphaProvider =
        new AlphaVantageAdapter(
            WebClient.builder().build(), "https://www.alphavantage.co", "demo-key");

    MarketDataProvider marketDataProvider =
        config.stockQuoteOrchestrator(twelveDataAdapterProvider, alphaProvider, massiveProvider);

    assertThat(marketDataProvider).isInstanceOf(StockQuoteOrchestrator.class);
  }
}
