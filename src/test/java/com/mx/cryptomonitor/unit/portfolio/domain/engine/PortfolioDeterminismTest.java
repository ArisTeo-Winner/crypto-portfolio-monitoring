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

class PortfolioDeterminismTest {

  private final PortfolioHoldingsAggregationEngine engine =
      new PortfolioHoldingsAggregationEngine();

  @Test
  void twoRunsWithSameInputProduceIdenticalSeries() {
    List<PortfolioAssetHistoryInput> input =
        List.of(
            input(
                "BTC",
                List.of(buy("BTC", "2026-01-01T00:00:00Z", "1")),
                price("2026-01-01T00:00:00Z", "100"),
                price("2026-01-02T00:00:00Z", "110"),
                price("2026-01-03T00:00:00Z", "120")),
            input(
                "ETH",
                List.of(buy("ETH", "2026-01-01T00:00:00Z", "3")),
                price("2026-01-01T00:00:00Z", "50"),
                price("2026-01-02T00:00:00Z", "55"),
                price("2026-01-03T00:00:00Z", "60")));

    List<TimeValuePoint> firstRun = engine.aggregate(input, Resolution.DAILY);
    List<TimeValuePoint> secondRun = engine.aggregate(input, Resolution.DAILY);

    assertThat(firstRun).isEqualTo(secondRun);
  }

  @Test
  void valuesUseBigDecimalWithScale2() {
    List<TimeValuePoint> result =
        engine.aggregate(
            List.of(
                input(
                    "BTC",
                    List.of(buy("BTC", "2026-01-01T00:00:00Z", "1")),
                    price("2026-01-01T00:00:00Z", "100.12345"))),
            Resolution.DAILY);

    assertThat(result).allMatch(p -> p.value().scale() == 2);
  }

  @Test
  void noBigDecimalPrecisionLossAcrossMultipleAssets() {
    // Three assets each contributing 0.005 → total should round to 0.02 (HALF_UP)
    List<TimeValuePoint> result =
        engine.aggregate(
            List.of(
                input(
                    "A",
                    List.of(buy("A", "2026-01-01T00:00:00Z", "1")),
                    price("2026-01-01T00:00:00Z", "0.005")),
                input(
                    "B",
                    List.of(buy("B", "2026-01-01T00:00:00Z", "1")),
                    price("2026-01-01T00:00:00Z", "0.005")),
                input(
                    "C",
                    List.of(buy("C", "2026-01-01T00:00:00Z", "1")),
                    price("2026-01-01T00:00:00Z", "0.005"))),
            Resolution.DAILY);

    assertThat(result).containsExactly(new TimeValuePoint(1767225600L, new BigDecimal("0.02")));
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
