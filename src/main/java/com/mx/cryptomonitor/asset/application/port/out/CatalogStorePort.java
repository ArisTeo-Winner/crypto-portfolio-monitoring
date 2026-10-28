package com.mx.cryptomonitor.asset.application.port.out;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import com.mx.cryptomonitor.asset.application.dto.AssetCatalogDto;

public interface CatalogStorePort {

  void saveEntry(AssetCatalogDto entry);

  void addToRanking(String rankingKey, String symbol, double score);

  List<String> getTopSymbols(String rankingKey, int limit);

  Optional<AssetCatalogDto> findEntry(String symbol);

  boolean isCatalogLoaded();

  void saveMiscEntry(AssetCatalogDto entry, Duration ttl);
}
