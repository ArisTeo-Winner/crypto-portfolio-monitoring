package com.mx.cryptomonitor;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModulithVerificationTest {

  @Test
  void shouldVerifyApplicationModules() {
    ApplicationModules.of(CryptoPortfolioMonitoringApplication.class).verify();
  }
}
