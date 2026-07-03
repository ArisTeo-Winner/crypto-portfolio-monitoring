package com.mx.cryptomonitor.unit.marketdata.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.mx.cryptomonitor.marketdata.application.port.out.BmvMarketDataPort;
import com.mx.cryptomonitor.marketdata.application.port.out.StockQuoteProvider;
import com.mx.cryptomonitor.marketdata.application.service.StockQuoteOrchestrator;
import com.mx.cryptomonitor.marketdata.infrastructure.outbound.alphavantage.AlphaVantageAdapter;
import com.mx.cryptomonitor.marketdata.infrastructure.outbound.databursatil.DataBursatilAdapter;
import com.mx.cryptomonitor.marketdata.infrastructure.outbound.massive.MassiveAdapter;

/**
 * Verifica que la incorporacion de DataBursatil/BMV no rompio los proveedores USD existentes ni su
 * orquestador de fallback.
 */
class ExistingProvidersRegressionTest {

  // -------------------------------------------------------------------------
  // C5a — AlphaVantage y Massive siguen implementando StockQuoteProvider
  // -------------------------------------------------------------------------

  @Test
  void alphaVantageAdapterStillImplementsStockQuoteProvider() {
    assertThat(StockQuoteProvider.class.isAssignableFrom(AlphaVantageAdapter.class)).isTrue();
  }

  @Test
  void massiveAdapterStillImplementsStockQuoteProvider() {
    assertThat(StockQuoteProvider.class.isAssignableFrom(MassiveAdapter.class)).isTrue();
  }

  // -------------------------------------------------------------------------
  // C5b — el orquestador USD sigue resolviendo cotizaciones sin tocar DataBursatil
  // -------------------------------------------------------------------------

  @Test
  void orchestratorResolvesUsdQuotesUsingOnlyRegisteredStockQuoteProviders() {
    StockQuoteProvider alphaVantage = mock(StockQuoteProvider.class);
    when(alphaVantage.providerName()).thenReturn("alphavantage");
    when(alphaVantage.getLatest("AAPL")).thenReturn(Optional.of(new BigDecimal("150.25")));

    StockQuoteOrchestrator orchestrator = new StockQuoteOrchestrator(List.of(alphaVantage));

    Optional<BigDecimal> result = orchestrator.getLatest("AAPL");

    assertThat(result).contains(new BigDecimal("150.25"));
  }

  // -------------------------------------------------------------------------
  // C5c — DataBursatil no participa del fallback USD: solo expone BmvMarketDataPort,
  // nunca StockQuoteProvider, por lo que el orquestador no puede seleccionarlo.
  // -------------------------------------------------------------------------

  @Test
  void dataBursatilAdapterExposesOnlyBmvMarketDataPortNotStockQuoteProvider() {
    assertThat(StockQuoteProvider.class.isAssignableFrom(DataBursatilAdapter.class)).isFalse();
    assertThat(BmvMarketDataPort.class.isAssignableFrom(DataBursatilAdapter.class)).isTrue();
  }
}
