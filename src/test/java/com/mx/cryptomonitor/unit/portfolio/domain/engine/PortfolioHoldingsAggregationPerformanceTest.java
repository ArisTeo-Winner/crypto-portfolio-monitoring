package com.mx.cryptomonitor.unit.portfolio.domain.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.mx.cryptomonitor.portfolio.domain.engine.PortfolioHoldingsAggregationEngine;
import com.mx.cryptomonitor.portfolio.domain.model.AssetType;
import com.mx.cryptomonitor.portfolio.domain.model.HistoricalPriceSeries;
import com.mx.cryptomonitor.portfolio.domain.model.PortfolioAccountingTransaction;
import com.mx.cryptomonitor.portfolio.domain.model.PortfolioAssetHistoryInput;
import com.mx.cryptomonitor.portfolio.domain.model.PricePoint;

class PortfolioHoldingsAggregationPerformanceTest {

  @Test
  void aggregatesFiveAssetsWithLargeTransactionAndPriceSetsUnderTwoSeconds() {
    PortfolioHoldingsAggregationEngine engine = new PortfolioHoldingsAggregationEngine();
    List<PortfolioAssetHistoryInput> assets = new ArrayList<>();
    Instant start = Instant.parse("2026-01-01T00:00:00Z");

    for (int assetIndex = 0; assetIndex < 5; assetIndex++) {
      String symbol = "ASSET" + assetIndex;
      List<PortfolioAccountingTransaction> transactions = new ArrayList<>();
      for (int transactionIndex = 0; transactionIndex < 400; transactionIndex++) {
        transactions.add(
            new PortfolioAccountingTransaction(
                start.plusSeconds(transactionIndex * 60L),
                symbol,
                AssetType.CRYPTO,
                "BUY",
                new BigDecimal("0.01"),
                BigDecimal.ONE,
                new BigDecimal("0.01"),
                BigDecimal.ZERO));
      }

      List<PricePoint> prices = new ArrayList<>();
      for (int priceIndex = 0; priceIndex < 10_000; priceIndex++) {
        prices.add(
            new PricePoint(
                start.plusSeconds(priceIndex * 60L),
                new BigDecimal("10").add(new BigDecimal(priceIndex % 100))));
      }
      assets.add(
          new PortfolioAssetHistoryInput(
              AssetType.CRYPTO,
              symbol,
              transactions,
              new HistoricalPriceSeries(AssetType.CRYPTO, symbol, prices)));
    }

    long started = System.nanoTime();
    assertThat(engine.aggregate(assets)).hasSize(10_000);
    Duration duration = Duration.ofNanos(System.nanoTime() - started);

    assertThat(duration).isLessThan(Duration.ofSeconds(2));
  }
}
