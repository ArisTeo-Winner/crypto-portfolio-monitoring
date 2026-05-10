package com.mx.cryptomonitor.portfolio.application.port.out;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.mx.cryptomonitor.portfolio.domain.model.AssetType;

public interface PriceHistoryCachePort {

  void storePriceHistory(
      AssetType assetType, String symbol, String range, List<PriceHistoryPoint> points, Duration ttl);

  List<PriceHistoryPoint> getPriceHistory(AssetType assetType, String symbol, String range);

  boolean acquireLoadLock(AssetType assetType, String symbol, String range, Duration ttl);

  void releaseLoadLock(AssetType assetType, String symbol, String range);

  record PriceHistoryPoint(Instant time, BigDecimal price) {}
}
