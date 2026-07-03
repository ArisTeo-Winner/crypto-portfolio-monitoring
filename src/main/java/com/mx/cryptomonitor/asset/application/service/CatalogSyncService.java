package com.mx.cryptomonitor.asset.application.service;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.mx.cryptomonitor.asset.application.dto.AssetCatalogDto;
import com.mx.cryptomonitor.asset.application.port.out.CatalogFetchPort;
import com.mx.cryptomonitor.asset.application.port.out.CatalogStorePort;
import com.mx.cryptomonitor.asset.domain.exception.CatalogFetchException;
import com.mx.cryptomonitor.asset.domain.model.AssetCatalogEntity;
import com.mx.cryptomonitor.asset.domain.repository.AssetCatalogRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CatalogSyncService {

  private static final Logger log = LoggerFactory.getLogger(CatalogSyncService.class);

  private static final List<AssetCatalogDto> STATIC_CRYPTOS =
      List.of(
          new AssetCatalogDto("BTC", "Bitcoin", "CRYPTO", null, null, "USD", 1_900_000L),
          new AssetCatalogDto("ETH", "Ethereum", "CRYPTO", null, null, "USD", 460_000L),
          new AssetCatalogDto("USDT", "Tether", "CRYPTO", null, null, "USD", 130_000L),
          new AssetCatalogDto("BNB", "BNB", "CRYPTO", null, null, "USD", 88_000L),
          new AssetCatalogDto("XRP", "XRP", "CRYPTO", null, null, "USD", 140_000L),
          new AssetCatalogDto("USDC", "USD Coin", "CRYPTO", null, null, "USD", 61_000L));

  private static final List<AssetCatalogDto> STATIC_GOV_BONDS =
      List.of(
          new AssetCatalogDto(
              "CETES28", "CETES 28 dias", "GOVERNMENT_BOND", null, "cetesdirecto", "MXN", null),
          new AssetCatalogDto(
              "CETES91", "CETES 91 dias", "GOVERNMENT_BOND", null, "cetesdirecto", "MXN", null),
          new AssetCatalogDto(
              "CETES182", "CETES 182 dias", "GOVERNMENT_BOND", null, "cetesdirecto", "MXN", null),
          new AssetCatalogDto(
              "CETES364", "CETES 364 dias", "GOVERNMENT_BOND", null, "cetesdirecto", "MXN", null),
          new AssetCatalogDto(
              "UDIBONO", "UDIBONO", "GOVERNMENT_BOND", null, "cetesdirecto", "MXN", null),
          new AssetCatalogDto(
              "BONDM", "Bonos M", "GOVERNMENT_BOND", null, "cetesdirecto", "MXN", null),
          new AssetCatalogDto(
              "TLT",
              "iShares 20+ Year Treasury Bond ETF",
              "GOVERNMENT_BOND",
              null,
              null,
              "USD",
              null),
          new AssetCatalogDto(
              "IEF",
              "iShares 7-10 Year Treasury Bond ETF",
              "GOVERNMENT_BOND",
              null,
              null,
              "USD",
              null),
          new AssetCatalogDto(
              "SHY",
              "iShares 1-3 Year Treasury Bond ETF",
              "GOVERNMENT_BOND",
              null,
              null,
              "USD",
              null),
          new AssetCatalogDto(
              "GOVT", "iShares US Treasury Bond ETF", "GOVERNMENT_BOND", null, null, "USD", null));

  private final CatalogFetchPort fmpAdapter;
  private final CatalogStorePort redisService;
  private final AssetCatalogRepository catalogRepository;

  @Scheduled(cron = "0 0 0 * * MON")
  public void syncWeekly() {
    log.info("CatalogSync: iniciando sync semanal");
    syncType("stock", 50);
    syncType("etf", 50);
    syncType("crypto", 50);
    syncType("government_bond", 50);
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

  public void forceFullSync() {
    syncWeekly();
  }

  public void syncType(String type, int limit) {
    List<AssetCatalogDto> data;
    try {
      data = fetchFor(type, limit);
    } catch (CatalogFetchException e) {
      log.error(
          "Sync {} abortado por error FMP, catalogo previo conservado: {}", type, e.getMessage());
      return;
    }
    if (data.isEmpty()) {
      log.warn("Sync {} devolvio 0 resultados, conservando catalogo previo", type);
      return;
    }
    String searchKey = "catalog:search:" + type.toLowerCase(Locale.ROOT);
    String top10Key = "catalog:top10:" + type.toLowerCase(Locale.ROOT);
    for (int i = 0; i < data.size(); i++) {
      AssetCatalogDto dto = data.get(i);
      catalogRepository.save(toEntity(dto));
      redisService.saveEntry(dto);
      double score = dto.marketCap() != null ? dto.marketCap() : (limit - i);
      redisService.addToRanking(searchKey, dto.symbol(), score);
      if (i < 10) {
        redisService.addToRanking(top10Key, dto.symbol(), score);
      }
    }
  }

  // Kept for backward compatibility with existing callers/tests.
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

  // Kept for backward compatibility with existing callers/tests.
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

  private List<AssetCatalogDto> fetchFor(String type, int limit) {
    return switch (type.toLowerCase(Locale.ROOT)) {
      case "stock" -> fmpAdapter.fetchTopStocks(limit);
      case "etf" -> fmpAdapter.fetchTopEtfs(limit);
      case "crypto" -> STATIC_CRYPTOS;
      case "government_bond" -> STATIC_GOV_BONDS;
      default -> List.of();
    };
  }

  private AssetCatalogEntity toEntity(AssetCatalogDto dto) {
    return AssetCatalogEntity.builder()
        .symbol(dto.symbol())
        .name(dto.name())
        .assetType(dto.assetType())
        .logoUrl(dto.logoUrl())
        .exchange(dto.exchange())
        .currency(dto.currency())
        .marketCap(dto.marketCap())
        .popular(false)
        .updatedAt(OffsetDateTime.now(ZoneOffset.UTC))
        .build();
  }
}
