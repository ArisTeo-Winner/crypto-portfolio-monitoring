package com.mx.cryptomonitor.portfolio.domain.model;

import java.util.List;

public record PortfolioAssetHistoryInput(
    AssetType assetType,
    String symbol,
    List<PortfolioAccountingTransaction> transactions,
    HistoricalPriceSeries priceSeries) {

  public PortfolioAssetHistoryInput {
    transactions = transactions == null ? List.of() : List.copyOf(transactions);
  }
}
