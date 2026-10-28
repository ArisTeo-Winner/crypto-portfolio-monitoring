package com.mx.cryptomonitor.unit.portfolio.domain.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.mx.cryptomonitor.portfolio.domain.engine.PortfolioHoldingsAggregationEngine;
import com.mx.cryptomonitor.portfolio.domain.model.AssetType;
import com.mx.cryptomonitor.portfolio.domain.model.HistoricalPriceSeries;
import com.mx.cryptomonitor.portfolio.domain.model.PortfolioAccountingTransaction;
import com.mx.cryptomonitor.portfolio.domain.model.PortfolioAssetHistoryInput;
import com.mx.cryptomonitor.portfolio.domain.model.PricePoint;
import com.mx.cryptomonitor.portfolio.domain.model.Resolution;
import com.mx.cryptomonitor.portfolio.domain.model.TimeValuePoint;

class PortfolioHistoricalContinuityTest {

  private final PortfolioHoldingsAggregationEngine engine =
      new PortfolioHoldingsAggregationEngine();

  @Test
  void seriesIsContiguousAcrossNonConsecutiveTransactionDays() {
    // BTC: prices on days 1, 3, 5. ETH: prices on days 2, 4.
    // Result must have 5 points (union of timestamps) with carry-forward.
    List<TimeValuePoint> result =
        engine.aggregate(
            List.of(
                input(
                    "BTC",
                    List.of(buy("BTC", "2026-01-01T00:00:00Z", "1")),
                    price("2026-01-01T00:00:00Z", "100"),
                    price("2026-01-03T00:00:00Z", "120"),
                    price("2026-01-05T00:00:00Z", "140")),
                input(
                    "ETH",
                    List.of(buy("ETH", "2026-01-02T00:00:00Z", "2")),
                    price("2026-01-02T00:00:00Z", "10"),
                    price("2026-01-04T00:00:00Z", "12"))),
            Resolution.DAILY);

    assertThat(result).hasSize(5);
    List<Long> times = result.stream().map(TimeValuePoint::time).toList();
    for (int i = 1; i < times.size(); i++) {
      assertThat(times.get(i)).isGreaterThan(times.get(i - 1));
    }
  }

  @Test
  void carryForwardFillsGapsInPriceSeries() {
    // BTC has a price gap on day 2. Day 2 value should use day 1 price.
    List<TimeValuePoint> result =
        engine.aggregate(
            List.of(
                input(
                    "BTC",
                    List.of(buy("BTC", "2026-01-01T00:00:00Z", "1")),
                    price("2026-01-01T00:00:00Z", "100"),
                    price("2026-01-03T00:00:00Z", "120")),
                input(
                    "ETH",
                    List.of(buy("ETH", "2026-01-01T00:00:00Z", "2")),
                    price("2026-01-01T00:00:00Z", "10"),
                    price("2026-01-02T00:00:00Z", "11"),
                    price("2026-01-03T00:00:00Z", "12"))),
            Resolution.DAILY);

    assertThat(result).hasSize(3);
    // Day 2: BTC carry-forward=$100, ETH=$22 → total=$122
    TimeValuePoint day2 =
        result.stream()
            .filter(p -> p.time() == Instant.parse("2026-01-02T00:00:00Z").getEpochSecond())
            .findFirst()
            .orElseThrow();
    assertThat(day2.value()).isEqualByComparingTo("122.00");
  }

  @Test
  void assetWithNoPricesContributesZeroToSeries() {
    List<TimeValuePoint> result =
        engine.aggregate(
            List.of(
                input(
                    "BTC",
                    List.of(buy("BTC", "2026-01-01T00:00:00Z", "1")),
                    price("2026-01-01T00:00:00Z", "100")),
                input("ETH", List.of(buy("ETH", "2026-01-01T00:00:00Z", "2")))),
            Resolution.DAILY);

    // ETH has no prices, contributes nothing — only BTC series
    assertThat(result).containsExactly(new TimeValuePoint(1767225600L, new BigDecimal("100.00")));
  }

  private PortfolioAssetHistoryInput input(
      String symbol, List<PortfolioAccountingTransaction> txs, PricePoint... prices) {
    return new PortfolioAssetHistoryInput(
        AssetType.CRYPTO,
        symbol,
        txs,
        new HistoricalPriceSeries(AssetType.CRYPTO, symbol, List.of(prices)));
  }

  private PortfolioAccountingTransaction buy(String symbol, String time, String quantity) {
    return new PortfolioAccountingTransaction(
        Instant.parse(time),
        symbol,
        AssetType.CRYPTO,
        "BUY",
        new BigDecimal(quantity),
        BigDecimal.ONE,
        new BigDecimal(quantity),
        BigDecimal.ZERO);
  }

  private PricePoint price(String time, String value) {
    return new PricePoint(Instant.parse(time), new BigDecimal(value));
  }
}
