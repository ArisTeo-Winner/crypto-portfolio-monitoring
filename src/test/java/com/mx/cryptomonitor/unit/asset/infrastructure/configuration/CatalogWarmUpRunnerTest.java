package com.mx.cryptomonitor.unit.asset.infrastructure.configuration;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;

import com.mx.cryptomonitor.asset.application.port.out.CatalogStorePort;
import com.mx.cryptomonitor.asset.application.service.CatalogSyncService;
import com.mx.cryptomonitor.asset.infrastructure.configuration.CatalogWarmUpRunner;

@ExtendWith(MockitoExtension.class)
class CatalogWarmUpRunnerTest {

  @Mock private CatalogStorePort redisService;
  @Mock private CatalogSyncService syncService;
  @Mock private ApplicationArguments args;

  @InjectMocks private CatalogWarmUpRunner runner;

  @Test
  void runTriggersSyncWhenCatalogIsNotLoaded() throws Exception {
    when(redisService.isCatalogLoaded()).thenReturn(false);

    runner.run(args);

    verify(syncService).forceFullSync();
  }

  @Test
  void runSkipsSyncWhenCatalogAlreadyLoaded() throws Exception {
    when(redisService.isCatalogLoaded()).thenReturn(true);

    runner.run(args);

    verify(syncService, never()).forceFullSync();
  }

  @Test
  void runLogsWarningAndDoesNotRethrowWhenSyncFails() throws Exception {
    when(redisService.isCatalogLoaded()).thenReturn(false);
    doThrow(new RuntimeException("FMP unavailable")).when(syncService).forceFullSync();

    // Must not throw — warm-up failure is non-fatal
    runner.run(args);

    verify(syncService).forceFullSync();
  }
}
