package com.mx.cryptomonitor.portfolio.application.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;

import com.mx.cryptomonitor.portfolio.application.port.out.PriceHistoryCachePort;
import com.mx.cryptomonitor.portfolio.domain.model.AssetType;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PriceHistoryCacheService {

  private static final Duration LOCK_TTL = Duration.ofSeconds(30);

  private final PriceHistoryCachePort priceHistoryCachePort;

  public List<PriceHistoryCachePort.PriceHistoryPoint> getPriceHistory(
      AssetType assetType, String symbol, int rangeDays) {
    return priceHistoryCachePort.getPriceHistory(assetType, symbol, rangeKey(rangeDays));
  }

  public boolean acquireLoadLock(AssetType assetType, String symbol, int rangeDays) {
    return priceHistoryCachePort.acquireLoadLock(assetType, symbol, rangeKey(rangeDays), LOCK_TTL);
  }

  public void storePriceHistory(
      AssetType assetType,
      String symbol,
      int rangeDays,
      List<PriceHistoryCachePort.PriceHistoryPoint> points) {
    priceHistoryCachePort.storePriceHistory(
        assetType, symbol, rangeKey(rangeDays), points, ttlForRange(rangeDays));
  }

  public void releaseLoadLock(AssetType assetType, String symbol, int rangeDays) {
    priceHistoryCachePort.releaseLoadLock(assetType, symbol, rangeKey(rangeDays));
  }

  public PriceHistoryCachePort.PriceHistoryPoint point(Instant time, BigDecimal price) {
    return new PriceHistoryCachePort.PriceHistoryPoint(time, price);
  }

  private Duration ttlForRange(int rangeDays) {
    return Duration.ofDays(Math.min(rangeDays + 1L, 366L));
  }

  private String rangeKey(int rangeDays) {
    return rangeDays == 365 ? "1y" : rangeDays + "d";
  }
}
