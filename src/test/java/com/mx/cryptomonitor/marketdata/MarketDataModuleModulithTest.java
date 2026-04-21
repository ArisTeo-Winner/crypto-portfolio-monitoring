package com.mx.cryptomonitor.marketdata;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

import com.mx.cryptomonitor.CryptoPortfolioMonitoringApplication;
import com.mx.cryptomonitor.marketdata.application.service.MarketDataReactiveService;

class MarketDataModuleModulithTest {

  @Test
  void shouldExposeMarketDataModuleInApplicationModulesModel() {
    ApplicationModules modules = ApplicationModules.of(CryptoPortfolioMonitoringApplication.class);

    var marketDataModule = modules.getModuleByType(MarketDataReactiveService.class);

    assertThat(marketDataModule).isPresent();
    assertThat(marketDataModule.orElseThrow().getBasePackage().getName())
        .isEqualTo("com.mx.cryptomonitor.marketdata");
  }
}
