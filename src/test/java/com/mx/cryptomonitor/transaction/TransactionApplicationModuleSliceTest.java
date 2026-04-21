package com.mx.cryptomonitor.transaction;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.test.ApplicationModuleTest;

@Disabled(
    "Pending cross-module cleanup in domain, user and infrastructure before enabling full Spring Modulith verification.")
@ApplicationModuleTest
class TransactionApplicationModuleSliceTest {

  @Test
  void pendingFullModuleVerification() {}
}
