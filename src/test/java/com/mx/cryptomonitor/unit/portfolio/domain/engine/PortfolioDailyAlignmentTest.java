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

class PortfolioDailyAlignmentTest {

  private static final long SECONDS_PER_DAY = 86400L;
  private final PortfolioHoldingsAggregationEngine engine =
      new PortfolioHoldingsAggregationEngine();

  @Test
  void allTimestampsAlignedToUtcDayStartWhenIntradayPricesProvided() {
    List<TimeValuePoint> result =
        engine.aggregate(
            List.of(
                input(
                    "BTC",
                    buy("BTC", "2026-01-01T00:00:00Z", "1"),
                    price("2026-01-01T02:30:00Z", "100"),
                    price("2026-01-02T03:15:00Z", "110"),
                    price("2026-01-03T18:45:00Z", "120"))),
            Resolution.DAILY);

    assertThat(result).isNotEmpty().allMatch(p -> p.time() % SECONDS_PER_DAY == 0);
  }

  @Test
  void timestampsAreStrictlyAscending() {
    List<TimeValuePoint> result =
        engine.aggregate(
            List.of(
                input(
                    "ETH",
                    buy("ETH", "2026-01-01T00:00:00Z", "2"),
                    price("2026-01-01T08:00:00Z", "50"),
                    price("2026-01-02T09:00:00Z", "55"),
                    price("2026-01-03T10:00:00Z", "60"))),
            Resolution.DAILY);

    List<Long> times = result.stream().map(TimeValuePoint::time).toList();
    for (int i = 1; i < times.size(); i++) {
      assertThat(times.get(i)).isGreaterThan(times.get(i - 1));
    }
  }

  @Test
  void twoPricesOnSameDayCollapseToSingleAlignedPoint() {
    List<TimeValuePoint> result =
        engine.aggregate(
            List.of(
                input(
                    "SOL",
                    buy("SOL", "2026-01-01T00:00:00Z", "5"),
                    price("2026-01-01T00:00:00Z", "20"),
                    price("2026-01-01T12:00:00Z", "21"),
                    price("2026-01-02T00:00:00Z", "22"))),
            Resolution.DAILY);

    assertThat(result).hasSize(2);
    assertThat(result.stream().map(TimeValuePoint::time)).allMatch(t -> t % SECONDS_PER_DAY == 0);
  }

  @Test
  void noPointExceedsTodayAlignedWhenSeriesEndsToday() {
    long todayAligned =
        Instant.now().getEpochSecond() - (Instant.now().getEpochSecond() % SECONDS_PER_DAY);

    List<TimeValuePoint> result =
        engine.aggregate(
            List.of(
                input(
                    "BTC",
                    buy("BTC", "2026-01-01T00:00:00Z", "1"),
                    price("2026-01-01T00:00:00Z", "100"),
                    price("2026-01-02T00:00:00Z", "110"))),
            Resolution.DAILY);

    assertThat(result).allMatch(p -> p.time() <= todayAligned + SECONDS_PER_DAY);
  }

  private PortfolioAssetHistoryInput input(
      String symbol, PortfolioAccountingTransaction tx, PricePoint... prices) {
    return new PortfolioAssetHistoryInput(
        AssetType.CRYPTO,
        symbol,
        List.of(tx),
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
