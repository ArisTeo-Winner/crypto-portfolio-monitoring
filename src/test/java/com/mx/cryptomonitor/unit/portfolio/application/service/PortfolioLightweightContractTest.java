package com.mx.cryptomonitor.unit.portfolio.application.service;

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

class PortfolioLightweightContractTest {

  private final PortfolioHoldingsAggregationEngine engine =
      new PortfolioHoldingsAggregationEngine();

  @Test
  void timeIsEpochSecondsAndPositive() {
    List<TimeValuePoint> result = buildSeries();

    assertThat(result).allMatch(p -> p.time() > 0);
  }

  @Test
  void noDuplicateTimestamps() {
    List<TimeValuePoint> result = buildSeries();
    List<Long> times = result.stream().map(TimeValuePoint::time).toList();

    assertThat(times).doesNotHaveDuplicates();
  }

  @Test
  void timestampsAreStrictlyAscending() {
    List<TimeValuePoint> result = buildSeries();
    List<Long> times = result.stream().map(TimeValuePoint::time).toList();

    for (int i = 1; i < times.size(); i++) {
      assertThat(times.get(i)).isGreaterThan(times.get(i - 1));
    }
  }

  @Test
  void valueIsNeverNull() {
    List<TimeValuePoint> result = buildSeries();

    assertThat(result).allMatch(p -> p.value() != null);
  }

  @Test
  void valueIsNonNegative() {
    List<TimeValuePoint> result = buildSeries();

    assertThat(result).allMatch(p -> p.value().compareTo(BigDecimal.ZERO) >= 0);
  }

  @Test
  void valueHasExactlyTwoDecimalPlaces() {
    List<TimeValuePoint> result = buildSeries();

    assertThat(result).allMatch(p -> p.value().scale() == 2);
  }

  @Test
  void emptyPortfolioReturnsEmptySeries() {
    List<TimeValuePoint> result = engine.aggregate(List.of(), Resolution.DAILY);

    assertThat(result).isEmpty();
  }

  private List<TimeValuePoint> buildSeries() {
    return engine.aggregate(
        List.of(
            input(
                "BTC",
                List.of(buy("BTC", "2026-01-01T00:00:00Z", "2")),
                price("2026-01-01T00:00:00Z", "100.00"),
                price("2026-01-02T00:00:00Z", "110.00"),
                price("2026-01-03T00:00:00Z", "120.00")),
            input(
                "ETH",
                List.of(buy("ETH", "2026-01-02T00:00:00Z", "5")),
                price("2026-01-02T00:00:00Z", "20.00"),
                price("2026-01-03T00:00:00Z", "22.00"))),
        Resolution.DAILY);
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
