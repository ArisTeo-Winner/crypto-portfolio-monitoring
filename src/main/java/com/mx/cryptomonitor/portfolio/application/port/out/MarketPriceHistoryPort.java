package com.mx.cryptomonitor.portfolio.application.port.out;

import java.util.List;

import com.mx.cryptomonitor.portfolio.domain.model.AssetType;
import com.mx.cryptomonitor.portfolio.domain.model.ChartResolution;
import com.mx.cryptomonitor.portfolio.domain.model.PricePoint;

public interface MarketPriceHistoryPort {

  List<PricePoint> getPriceHistory(AssetType assetType, String symbol, String range);

  List<PricePoint> getPriceHistory(
      AssetType assetType, String symbol, ChartResolution chartResolution);
}
