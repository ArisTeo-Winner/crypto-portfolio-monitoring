package com.mx.cryptomonitor.asset.infrastructure.configuration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.mx.cryptomonitor.asset.application.port.out.CatalogStorePort;
import com.mx.cryptomonitor.asset.application.service.CatalogSyncService;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CatalogWarmUpRunner implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(CatalogWarmUpRunner.class);

  private final CatalogStorePort redisService;
  private final CatalogSyncService syncService;

  @Override
  public void run(ApplicationArguments args) {
    if (!redisService.isCatalogLoaded()) {
      log.info("CatalogWarmUp: Redis vacio, cargando catalogo desde API...");
      try {
        syncService.forceFullSync();
        log.info("CatalogWarmUp: catalogo cargado exitosamente");
      } catch (Exception e) {
        log.warn(
            "CatalogWarmUp: no se pudo cargar catalogo desde API — "
                + "el servicio de busqueda retornara resultados vacios hasta "
                + "que Redis este disponible. Error: {}",
            e.getMessage());
      }
    }
  }
}
