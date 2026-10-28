package com.mx.cryptomonitor.portfolio.domain.model;

import java.util.List;

public record HistoricalPriceSeries(AssetType assetType, String symbol, List<PricePoint> points) {

  public HistoricalPriceSeries {
    points = points == null ? List.of() : List.copyOf(points);
  }
}
