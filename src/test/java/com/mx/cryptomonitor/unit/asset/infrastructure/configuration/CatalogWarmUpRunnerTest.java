package com.mx.cryptomonitor.unit.asset.infrastructure.configuration;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;

import com.mx.cryptomonitor.asset.application.dto.AssetCatalogDto;
import com.mx.cryptomonitor.asset.application.port.out.CatalogStorePort;
import com.mx.cryptomonitor.asset.application.service.CatalogSyncService;
import com.mx.cryptomonitor.asset.domain.model.AssetCatalogEntity;
import com.mx.cryptomonitor.asset.domain.repository.AssetCatalogRepository;
import com.mx.cryptomonitor.asset.infrastructure.configuration.CatalogWarmUpRunner;

@ExtendWith(MockitoExtension.class)
class CatalogWarmUpRunnerTest {

  @Mock private CatalogStorePort redisService;
  @Mock private CatalogSyncService syncService;
  @Mock private AssetCatalogRepository catalogRepository;
  @Mock private ApplicationArguments args;

  @InjectMocks private CatalogWarmUpRunner runner;

  @Test
  void runLoadsFromDbAndRefreshesFmpWhenCatalogNotLoaded() throws Exception {
    when(redisService.isCatalogLoaded()).thenReturn(false);
    when(catalogRepository.findAll()).thenReturn(List.of());

    runner.run(args);

    verify(catalogRepository).findAll();
    verify(syncService).forceFullSync();
  }

  @Test
  void runRecoversCatalogFromDbEntitiesIntoRedis() throws Exception {
    AssetCatalogEntity entity =
        AssetCatalogEntity.builder()
            .symbol("BTC")
            .name("Bitcoin")
            .assetType("CRYPTO")
            .currency("USD")
            .marketCap(1_900_000L)
            .popular(false)
            .updatedAt(OffsetDateTime.now())
            .build();
    when(redisService.isCatalogLoaded()).thenReturn(false);
    when(catalogRepository.findAll()).thenReturn(List.of(entity));

    runner.run(args);

    verify(redisService).saveEntry(any(AssetCatalogDto.class));
    verify(redisService).addToRanking(anyString(), anyString(), anyDouble());
    verify(syncService).forceFullSync();
  }

  @Test
  void runSkipsDbLoadButStillRefreshesFmpWhenCatalogAlreadyLoaded() throws Exception {
    when(redisService.isCatalogLoaded()).thenReturn(true);

    runner.run(args);

    verify(catalogRepository, never()).findAll();
    verify(syncService).forceFullSync();
  }

  @Test
  void runLogsWarningAndDoesNotRethrowWhenSyncFails() throws Exception {
    when(redisService.isCatalogLoaded()).thenReturn(false);
    when(catalogRepository.findAll()).thenReturn(List.of());
    doThrow(new RuntimeException("FMP unavailable")).when(syncService).forceFullSync();

    // Must not throw — warm-up failure is non-fatal
    runner.run(args);

    verify(syncService).forceFullSync();
  }

  @Test
  void runAlwaysTriesFmpRefreshEvenWhenCatalogIsLoaded() throws Exception {
    when(redisService.isCatalogLoaded()).thenReturn(true);
    doThrow(new RuntimeException("FMP down")).when(syncService).forceFullSync();

    // FMP failure must not propagate even when catalog was already loaded
    runner.run(args);

    verify(syncService).forceFullSync();
  }
}
