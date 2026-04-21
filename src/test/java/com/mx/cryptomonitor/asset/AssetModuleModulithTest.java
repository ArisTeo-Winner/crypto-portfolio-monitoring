package com.mx.cryptomonitor.asset;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

import com.mx.cryptomonitor.CryptoPortfolioMonitoringApplication;
import com.mx.cryptomonitor.asset.application.service.AssetSearchService;

class AssetModuleModulithTest {

  @Test
  void shouldExposeAssetModuleInApplicationModulesModel() {
    ApplicationModules modules = ApplicationModules.of(CryptoPortfolioMonitoringApplication.class);

    var assetModule = modules.getModuleByType(AssetSearchService.class);

    assertThat(assetModule).isPresent();
    assertThat(assetModule.orElseThrow().getBasePackage().getName())
        .isEqualTo("com.mx.cryptomonitor.asset");
  }
}
