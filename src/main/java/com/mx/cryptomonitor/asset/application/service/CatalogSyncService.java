package com.mx.cryptomonitor.asset.application.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.mx.cryptomonitor.asset.application.dto.AssetCatalogDto;
import com.mx.cryptomonitor.asset.application.port.out.CatalogFetchPort;
import com.mx.cryptomonitor.asset.application.port.out.CatalogStorePort;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CatalogSyncService {

  private static final Logger log = LoggerFactory.getLogger(CatalogSyncService.class);

  private final CatalogFetchPort fmpAdapter;
  private final CatalogStorePort redisService;

  @Scheduled(cron = "0 0 0 * * MON")
  public void syncWeekly() {
    log.info("CatalogSync: iniciando sync semanal");
    syncStocks(50);
    syncEtfs(50);
    log.info("CatalogSync: sync semanal completado");
  }

  @Scheduled(cron = "0 0 6 * * *")
  public void syncDailyRanking() {
    log.info("CatalogSync: actualizando ranking diario top 10");
    List<AssetCatalogDto> stocks = fmpAdapter.fetchTopStocks(10);
    stocks.forEach(
        dto ->
            redisService.addToRanking(
                "catalog:top10:stock",
                dto.symbol(),
                dto.marketCap() != null ? dto.marketCap() : 0));
  }

  public void syncStocks(int limit) {
    List<AssetCatalogDto> stocks = fmpAdapter.fetchTopStocks(limit);
    if (stocks.isEmpty()) {
      log.warn("CatalogSync: FMP devolvio 0 stocks");
      return;
    }
    for (int i = 0; i < stocks.size(); i++) {
      AssetCatalogDto dto = stocks.get(i);
      redisService.saveEntry(dto);
      redisService.addToRanking(
          "catalog:search:stock",
          dto.symbol(),
          dto.marketCap() != null ? dto.marketCap() : (limit - i));
      if (i < 10) {
        redisService.addToRanking(
            "catalog:top10:stock",
            dto.symbol(),
            dto.marketCap() != null ? dto.marketCap() : (10 - i));
      }
    }
  }

  public void syncEtfs(int limit) {
    List<AssetCatalogDto> etfs = fmpAdapter.fetchTopEtfs(limit);
    if (etfs.isEmpty()) {
      log.warn("CatalogSync: FMP devolvio 0 ETFs");
      return;
    }
    for (int i = 0; i < etfs.size(); i++) {
      AssetCatalogDto dto = etfs.get(i);
      redisService.saveEntry(dto);
      redisService.addToRanking("catalog:search:etf", dto.symbol(), limit - i);
      if (i < 10) redisService.addToRanking("catalog:top10:etf", dto.symbol(), 10 - i);
    }
  }

  public void forceFullSync() {
    syncWeekly();
  }
}
