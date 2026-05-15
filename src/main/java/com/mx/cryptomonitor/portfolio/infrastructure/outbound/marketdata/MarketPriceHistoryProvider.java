package com.mx.cryptomonitor.portfolio.infrastructure.outbound.marketdata;

import java.util.List;

import com.mx.cryptomonitor.portfolio.application.service.HoldingsHistoryRange;
import com.mx.cryptomonitor.portfolio.domain.model.AssetType;
import com.mx.cryptomonitor.portfolio.domain.model.ChartResolution;
import com.mx.cryptomonitor.portfolio.domain.model.PricePoint;

public interface MarketPriceHistoryProvider {

  boolean supports(AssetType assetType);

  List<PricePoint> fetchPriceHistory(
      AssetType assetType, String symbol, HoldingsHistoryRange range);

  default List<PricePoint> fetchPriceHistory(
      AssetType assetType, String symbol, ChartResolution chartResolution) {
    long days =
        java.time.Duration.between(chartResolution.start(), chartResolution.end()).toDays();
    String rangeStr;
    if (days > 365) rangeStr = "all";
    else if (days > 180) rangeStr = "1y";
    else if (days > 90) rangeStr = "180d";
    else if (days > 30) rangeStr = "90d";
    else if (days > 7) rangeStr = "30d";
    else if (days > 1) rangeStr = "7d";
    else rangeStr = "24h";
    return fetchPriceHistory(assetType, symbol, HoldingsHistoryRange.parse(rangeStr));
  }
}
