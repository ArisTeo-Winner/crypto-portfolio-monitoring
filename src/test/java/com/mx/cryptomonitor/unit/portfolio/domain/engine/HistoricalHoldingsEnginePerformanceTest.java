package com.mx.cryptomonitor.unit.portfolio.domain.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.mx.cryptomonitor.portfolio.domain.engine.HoldingsValueCalculator;
import com.mx.cryptomonitor.portfolio.domain.engine.PortfolioQuantityTimelineEngine;
import com.mx.cryptomonitor.portfolio.domain.model.AssetType;
import com.mx.cryptomonitor.portfolio.domain.model.PortfolioAccountingTransaction;
import com.mx.cryptomonitor.portfolio.domain.model.PricePoint;
import com.mx.cryptomonitor.portfolio.domain.model.QuantityTimelinePoint;
import com.mx.cryptomonitor.portfolio.domain.model.TimeValuePoint;

class HistoricalHoldingsEnginePerformanceTest {

  @Test
  void shouldRemainLinearForLargeTransactionAndPriceInputs() {
    PortfolioQuantityTimelineEngine quantityEngine = new PortfolioQuantityTimelineEngine();
    HoldingsValueCalculator calculator = new HoldingsValueCalculator();
    List<PortfolioAccountingTransaction> transactions = new ArrayList<>();
    List<PricePoint> prices = new ArrayList<>();
    Instant start = Instant.parse("2024-01-01T00:00:00Z");

    for (int index = 0; index < 2_000; index++) {
      transactions.add(
          new PortfolioAccountingTransaction(
              start.plusSeconds(index * 60L),
              "BTC",
              AssetType.CRYPTO,
              index % 5 == 4 ? "SELL" : "BUY",
              new BigDecimal("1"),
              new BigDecimal("100"),
              new BigDecimal("100"),
              BigDecimal.ZERO));
    }

    for (int index = 0; index < 10_000; index++) {
      prices.add(
          new PricePoint(
              start.plusSeconds(index * 300L),
              new BigDecimal("100").add(BigDecimal.valueOf(index % 50L))));
    }

    org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(
        Duration.ofSeconds(3),
        () -> {
          List<QuantityTimelinePoint> timeline = quantityEngine.buildQuantityTimeline(transactions);
          List<TimeValuePoint> series = calculator.calculate(prices, timeline);
          assertThat(series).hasSize(10_000);
        });
  }
}
