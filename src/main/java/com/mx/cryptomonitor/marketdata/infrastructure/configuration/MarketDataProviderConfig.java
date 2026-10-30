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
import com.mx.cryptomonitor.marketdata.application.port.out.StockTimeSeriesPort;
import com.mx.cryptomonitor.marketdata.application.service.StockQuoteOrchestrator;
import com.mx.cryptomonitor.marketdata.infrastructure.outbound.alphavantage.AlphaVantageAdapter;
import com.mx.cryptomonitor.marketdata.infrastructure.outbound.eodhd.EodhdAdapter;
import com.mx.cryptomonitor.marketdata.infrastructure.outbound.massive.MassiveAdapter;
import com.mx.cryptomonitor.marketdata.infrastructure.outbound.twelvedata.TwelveDataAdapter;

@Configuration
@EnableConfigurationProperties({
  AlphaVantageProperties.class,
  EodhdProperties.class,
  MassiveProperties.class,
  TwelveDataProperties.class
})
public class MarketDataProviderConfig {

  /**
   * Instancia única de {@link TwelveDataAdapter} cuando la API key está configurada.
   *
   * <p>Implementa AMBAS interfaces ({@link StockQuoteProvider} y {@link StockTimeSeriesPort}):
   * Spring registra el bean bajo el tipo declarado ({@code TwelveDataAdapter}), lo que permite
   * inyectarlo por cualquiera de sus interfaces sin crear duplicados.
   *
   * <p>Se declara como {@code TwelveDataAdapter} (tipo concreto) para que Spring lo encuentre
   * cuando se piden sus dos interfaces desde otros beans.
   */
  @Bean
  @ConditionalOnExpression(
      "'${marketdata.twelvedata.base-url:}' != '' and '${marketdata.twelvedata.api-key:}' != ''")
  public TwelveDataAdapter twelveDataAdapter(
      WebClient webClient, TwelveDataProperties twelveDataProperties) {
    return new TwelveDataAdapter(
        webClient, twelveDataProperties.getBaseUrl(), twelveDataProperties.getApiKey());
  }

  /** Proveedor secundario: Alpha Vantage. */
  @Bean
  public StockQuoteProvider alphaVantageStockQuoteProvider(
      WebClient webClient, AlphaVantageProperties alphaVantageProperties) {
    return new AlphaVantageAdapter(
        webClient, alphaVantageProperties.getBaseUrl(), alphaVantageProperties.getApiKey());
  }

  /** Proveedor terciario: Massive/Polygon.io (opcional). */
  @Bean
  @ConditionalOnExpression(
      "'${marketdata.massive.base-url:}' != '' and '${marketdata.massive.api-key:}' != ''")
  public StockQuoteProvider massiveStockQuoteProvider(
      WebClient webClient, MassiveProperties massiveProperties) {
    return new MassiveAdapter(
        webClient, massiveProperties.getBaseUrl(), massiveProperties.getApiKey());
  }

  /**
   * Proveedor PRIMARIO de precio de hoy: EODHD real-time (ADR-0016, opcional). Delante de la cadena
   * para corregir el rezago de dia previo; si no devuelve dato, el orquestador cae a los demas.
   */
  @Bean
  @ConditionalOnExpression("'${eodhd.base-url:}' != '' and '${eodhd.api-token:}' != ''")
  public StockQuoteProvider eodhdStockQuoteProvider(
      WebClient webClient, EodhdProperties eodhdProperties) {
    return new EodhdAdapter(
        webClient,
        eodhdProperties.getBaseUrl(),
        eodhdProperties.getApiToken(),
        eodhdProperties.getDefaultSuffix());
  }

  /**
   * Orquestador de fallback. Orden de consulta:
   *
   * <ol>
   *   <li>EODHD (si {@code EODHD_API_TOKEN} está configurado) — precio de hoy, ADR-0016
   *   <li>Twelve Data (si {@code TWELVEDATA_API_KEY} está configurada)
   *   <li>Alpha Vantage
   *   <li>Massive/Polygon (si {@code MASSIVE_API_KEY} está configurada)
   * </ol>
   */
  @Bean
  @Primary
  public MarketDataProvider stockQuoteOrchestrator(
      @Qualifier("eodhdStockQuoteProvider")
          ObjectProvider<StockQuoteProvider> eodhdStockQuoteProvider,
      ObjectProvider<TwelveDataAdapter> twelveDataAdapter,
      @Qualifier("alphaVantageStockQuoteProvider")
          StockQuoteProvider alphaVantageStockQuoteProvider,
      @Qualifier("massiveStockQuoteProvider")
          ObjectProvider<StockQuoteProvider> massiveStockQuoteProvider) {
    List<StockQuoteProvider> providers = new ArrayList<>();
    eodhdStockQuoteProvider.ifAvailable(providers::add);
    twelveDataAdapter.ifAvailable(providers::add);
    providers.add(alphaVantageStockQuoteProvider);
    massiveStockQuoteProvider.ifAvailable(providers::add);
    return new StockQuoteOrchestrator(providers);
  }
}
