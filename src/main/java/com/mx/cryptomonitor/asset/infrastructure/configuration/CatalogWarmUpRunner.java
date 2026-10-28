package com.mx.cryptomonitor.asset.infrastructure.configuration;

import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.mx.cryptomonitor.asset.application.dto.AssetCatalogDto;
import com.mx.cryptomonitor.asset.application.port.out.CatalogStorePort;
import com.mx.cryptomonitor.asset.application.service.CatalogSyncService;
import com.mx.cryptomonitor.asset.domain.model.AssetCatalogEntity;
import com.mx.cryptomonitor.asset.domain.repository.AssetCatalogRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CatalogWarmUpRunner implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(CatalogWarmUpRunner.class);

  private final CatalogStorePort redisService;
  private final CatalogSyncService syncService;
  private final AssetCatalogRepository catalogRepository;

  @Override
  public void run(ApplicationArguments args) {
    if (!redisService.isCatalogLoaded()) {
      List<AssetCatalogEntity> persisted = catalogRepository.findAll();
      if (!persisted.isEmpty()) {
        persisted.forEach(
            entity -> {
              AssetCatalogDto dto = toDto(entity);
              redisService.saveEntry(dto);
              String type = entity.getAssetType().toLowerCase(Locale.ROOT);
              double score = entity.getMarketCap() != null ? entity.getMarketCap() : 0.0;
              redisService.addToRanking("catalog:search:" + type, entity.getSymbol(), score);
              if (entity.isPopular()) {
                redisService.addToRanking("catalog:top10:" + type, entity.getSymbol(), score);
              }
            });
        log.info("Catalogo recuperado desde PostgreSQL: {} activos", persisted.size());
      } else {
        log.warn("PostgreSQL vacio (la semilla Flyway deberia haberlo poblado)");
      }
    }

    // FMP refresh always attempted — failure is non-fatal since PostgreSQL data is already in
    // Redis.
    try {
      syncService.forceFullSync();
    } catch (Exception e) {
      log.warn("Refresco FMP fallo, usando catalogo persistido: {}", e.getMessage());
    }
  }

  private AssetCatalogDto toDto(AssetCatalogEntity entity) {
    return new AssetCatalogDto(
        entity.getSymbol(),
        entity.getName(),
        entity.getAssetType(),
        entity.getLogoUrl(),
        entity.getExchange(),
        entity.getCurrency(),
        entity.getMarketCap());
  }
}
