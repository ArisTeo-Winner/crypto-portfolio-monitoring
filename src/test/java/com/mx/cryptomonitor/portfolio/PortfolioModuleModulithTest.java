package com.mx.cryptomonitor.portfolio;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

import com.mx.cryptomonitor.CryptoPortfolioMonitoringApplication;
import com.mx.cryptomonitor.portfolio.application.service.PortfolioService;

class PortfolioModuleModulithTest {

  @Test
  void shouldExposePortfolioModuleInApplicationModulesModel() {
    ApplicationModules modules = ApplicationModules.of(CryptoPortfolioMonitoringApplication.class);

    var portfolioModule = modules.getModuleByType(PortfolioService.class);

    assertThat(portfolioModule).isPresent();
    assertThat(portfolioModule.orElseThrow().getBasePackage().getName())
        .isEqualTo("com.mx.cryptomonitor.portfolio");
  }
}
