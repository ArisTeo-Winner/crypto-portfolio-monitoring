package com.mx.cryptomonitor.shared;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

import com.mx.cryptomonitor.CryptoPortfolioMonitoringApplication;
import com.mx.cryptomonitor.shared.infrastructure.config.CacheConfig;

class SharedModuleModulithTest {

  @Test
  void shouldExposeSharedModuleInApplicationModulesModel() {
    ApplicationModules modules = ApplicationModules.of(CryptoPortfolioMonitoringApplication.class);

    var sharedModule = modules.getModuleByType(CacheConfig.class);

    assertThat(sharedModule).isPresent();
    assertThat(sharedModule.orElseThrow().getBasePackage().getName())
        .isEqualTo("com.mx.cryptomonitor.shared");
  }
}
