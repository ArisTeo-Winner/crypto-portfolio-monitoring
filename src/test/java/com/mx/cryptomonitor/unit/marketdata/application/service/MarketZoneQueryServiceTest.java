package com.mx.cryptomonitor.unit.marketdata.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.mx.cryptomonitor.marketdata.application.service.MarketZoneQueryService;

class MarketZoneQueryServiceTest {

  private final MarketZoneQueryService service = new MarketZoneQueryService();

  @Test
  void mapsMxnOrBmvToMexicoCity() {
    assertThat(service.zoneIdFor("MXN", null)).contains("America/Mexico_City");
    assertThat(service.zoneIdFor(null, "BMV")).contains("America/Mexico_City");
  }

  @Test
  void mapsUsdToNewYork() {
    assertThat(service.zoneIdFor("USD", "NASDAQ")).contains("America/New_York");
  }

  @Test
  void returnsEmptyWhenNoPhysicalMarket() {
    // Crypto u origen desconocido: el consumidor aplica UTC.
    assertThat(service.zoneIdFor("USDT", "BINANCE")).isEmpty();
    assertThat(service.zoneIdFor(null, null)).isEmpty();
  }
}
