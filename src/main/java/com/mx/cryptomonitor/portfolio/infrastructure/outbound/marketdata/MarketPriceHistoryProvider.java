package com.mx.cryptomonitor.portfolio.infrastructure.outbound.marketdata;

import java.util.List;

import com.mx.cryptomonitor.portfolio.application.service.HoldingsHistoryRange;
import com.mx.cryptomonitor.portfolio.domain.model.AssetType;
import com.mx.cryptomonitor.portfolio.domain.model.PricePoint;

public interface MarketPriceHistoryProvider {

  boolean supports(AssetType assetType);

  List<PricePoint> fetchPriceHistory(AssetType assetType, String symbol, HoldingsHistoryRange range);
}
