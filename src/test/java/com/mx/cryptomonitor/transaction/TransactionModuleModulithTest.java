package com.mx.cryptomonitor.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

import com.mx.cryptomonitor.CryptoPortfolioMonitoringApplication;
import com.mx.cryptomonitor.transaction.application.port.in.TransactionCommandUseCase;
import com.mx.cryptomonitor.transaction.application.port.in.TransactionQueryUseCase;

class TransactionModuleModulithTest {

  @Test
  void shouldExposeTransactionModuleThroughInputPortsInApplicationModulesModel() {
    ApplicationModules modules = ApplicationModules.of(CryptoPortfolioMonitoringApplication.class);

    var commandModule = modules.getModuleByType(TransactionCommandUseCase.class);
    var queryModule = modules.getModuleByType(TransactionQueryUseCase.class);

    assertThat(commandModule).isPresent();
    assertThat(queryModule).isPresent();
    assertThat(commandModule.orElseThrow().getBasePackage().getName())
        .isEqualTo("com.mx.cryptomonitor.transaction");
    assertThat(queryModule.orElseThrow().getBasePackage().getName())
        .isEqualTo("com.mx.cryptomonitor.transaction");
  }
}
