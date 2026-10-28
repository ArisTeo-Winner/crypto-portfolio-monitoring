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
import com.mx.cryptomonitor.portfolio.domain.model.TimeValuePoint;

class PortfolioHoldingsAggregationEngineTest {

  private final PortfolioHoldingsAggregationEngine engine =
      new PortfolioHoldingsAggregationEngine();

  @Test
  void aggregatesMultiAssetSeriesWithSameTimestamp() {
    List<TimeValuePoint> result =
        engine.aggregate(
            List.of(
                input(
                    "BTC",
                    buy("BTC", "2026-01-01T00:00:00Z", "1"),
                    price("2026-01-01T00:00:00Z", "100")),
                input(
                    "ETH",
                    buy("ETH", "2026-01-01T00:00:00Z", "2"),
                    price("2026-01-01T00:00:00Z", "50"))));

    assertThat(result).containsExactly(new TimeValuePoint(1767225600L, new BigDecimal("200.00")));
  }

  @Test
  void usesUnionOfDifferentTimestampsAndCarriesForwardLastKnownValue() {
    List<TimeValuePoint> result =
        engine.aggregate(
            List.of(
                input(
                    "BTC",
                    buy("BTC", "2026-01-01T00:00:00Z", "1"),
                    price("2026-01-01T00:00:00Z", "100"),
                    price("2026-01-03T00:00:00Z", "120")),
                input(
                    "ETH",
                    buy("ETH", "2026-01-01T00:00:00Z", "2"),
                    price("2026-01-02T00:00:00Z", "10"))));

    assertThat(result)
        .containsExactly(
            new TimeValuePoint(1767225600L, new BigDecimal("100.00")),
            new TimeValuePoint(1767312000L, new BigDecimal("120.00")),
            new TimeValuePoint(1767398400L, new BigDecimal("140.00")));
  }

  @Test
  void isDeterministicForSameInput() {
    List<PortfolioAssetHistoryInput> input =
        List.of(
            input(
                "SOL",
                buy("SOL", "2026-01-01T00:00:00Z", "3"),
                price("2026-01-02T00:00:00Z", "10"),
                price("2026-01-03T00:00:00Z", "11")));

    assertThat(engine.aggregate(input)).isEqualTo(engine.aggregate(input));
  }

  @Test
  void returnsZeroCurveWhenQuantityIsZeroForWholeRange() {
    List<TimeValuePoint> result =
        engine.aggregate(
            List.of(
                input(
                    "SOL",
                    List.of(),
                    price("2026-01-01T00:00:00Z", "10"),
                    price("2026-01-02T00:00:00Z", "11"))));

    assertThat(result)
        .containsExactly(
            new TimeValuePoint(1767225600L, new BigDecimal("0.00")),
            new TimeValuePoint(1767312000L, new BigDecimal("0.00")));
  }

  @Test
  void appliesFinalRoundingAfterAggregatingAssets() {
    List<TimeValuePoint> result =
        engine.aggregate(
            List.of(
                input(
                    "A",
                    buy("A", "2026-01-01T00:00:00Z", "1"),
                    price("2026-01-01T00:00:00Z", "0.005")),
                input(
                    "B",
                    buy("B", "2026-01-01T00:00:00Z", "1"),
                    price("2026-01-01T00:00:00Z", "0.005")),
                input(
                    "C",
                    buy("C", "2026-01-01T00:00:00Z", "1"),
                    price("2026-01-01T00:00:00Z", "0.005"))));

    assertThat(result).containsExactly(new TimeValuePoint(1767225600L, new BigDecimal("0.02")));
  }

  private PortfolioAssetHistoryInput input(
      String symbol, PortfolioAccountingTransaction transaction, PricePoint... prices) {
    return input(symbol, List.of(transaction), prices);
  }

  private PortfolioAssetHistoryInput input(
      String symbol, List<PortfolioAccountingTransaction> transactions, PricePoint... prices) {
    return new PortfolioAssetHistoryInput(
        AssetType.CRYPTO,
        symbol,
        transactions,
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

  private PricePoint price(String time, String price) {
    return new PricePoint(Instant.parse(time), new BigDecimal(price));
  }
}
