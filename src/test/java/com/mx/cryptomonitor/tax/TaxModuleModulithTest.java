package com.mx.cryptomonitor.tax;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

import com.mx.cryptomonitor.CryptoPortfolioMonitoringApplication;
import com.mx.cryptomonitor.tax.application.service.AnnualEquityTaxService;

class TaxModuleModulithTest {

  @Test
  void shouldExposeTaxModuleInApplicationModulesModel() {
    ApplicationModules modules = ApplicationModules.of(CryptoPortfolioMonitoringApplication.class);

    var taxModule = modules.getModuleByType(AnnualEquityTaxService.class);

    assertThat(taxModule).isPresent();
    assertThat(taxModule.orElseThrow().getBasePackage().getName())
        .isEqualTo("com.mx.cryptomonitor.tax");
  }
}
