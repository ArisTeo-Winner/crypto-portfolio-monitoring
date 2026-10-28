package com.mx.cryptomonitor.portfolio.domain.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import com.mx.cryptomonitor.portfolio.domain.model.PortfolioAssetHistoryInput;
import com.mx.cryptomonitor.portfolio.domain.model.Resolution;
import com.mx.cryptomonitor.portfolio.domain.model.TimeValuePoint;

public class PortfolioHoldingsAggregationEngine {

  private static final int PORTFOLIO_SCALE = 2;
  private static final long SECONDS_PER_DAY = 86400L;

  private final PortfolioQuantityTimelineEngine quantityTimelineEngine;
  private final HoldingsValueCalculator holdingsValueCalculator;

  public PortfolioHoldingsAggregationEngine() {
    this(new PortfolioQuantityTimelineEngine(), new HoldingsValueCalculator());
  }

  PortfolioHoldingsAggregationEngine(
      PortfolioQuantityTimelineEngine quantityTimelineEngine,
      HoldingsValueCalculator holdingsValueCalculator) {
    this.quantityTimelineEngine = quantityTimelineEngine;
    this.holdingsValueCalculator = holdingsValueCalculator;
  }

  public List<TimeValuePoint> aggregate(List<PortfolioAssetHistoryInput> assets) {
    return aggregate(assets, null);
  }

  public List<TimeValuePoint> aggregate(
      List<PortfolioAssetHistoryInput> assets, Resolution resolution) {
    if (assets == null || assets.isEmpty()) {
      return List.of();
    }

    List<List<TimeValuePoint>> valueSeries =
        assets.stream()
            .map(this::calculateAssetSeries)
            .map(series -> alignSeries(series, resolution))
            .filter(series -> !series.isEmpty())
            .toList();
    if (valueSeries.isEmpty()) {
      return List.of();
    }

    TreeSet<Long> timestamps = new TreeSet<>();
    valueSeries.forEach(series -> series.forEach(point -> timestamps.add(point.time())));

    List<SeriesCursor> cursors = valueSeries.stream().map(SeriesCursor::new).toList();
    List<TimeValuePoint> aggregated = new ArrayList<>(timestamps.size());
    for (Long timestamp : timestamps) {
      BigDecimal total = BigDecimal.ZERO;
      for (SeriesCursor cursor : cursors) {
        total = total.add(cursor.valueAt(timestamp));
      }
      aggregated.add(
          new TimeValuePoint(timestamp, total.setScale(PORTFOLIO_SCALE, RoundingMode.HALF_UP)));
    }
    return aggregated;
  }

  static long alignToUtcDayStart(long epochSeconds) {
    return epochSeconds - (epochSeconds % SECONDS_PER_DAY);
  }

  private static List<TimeValuePoint> alignSeries(
      List<TimeValuePoint> series, Resolution resolution) {
    if (resolution == null || resolution != Resolution.DAILY) {
      return series;
    }
    LinkedHashMap<Long, BigDecimal> byDay = new LinkedHashMap<>();
    for (TimeValuePoint point : series) {
      byDay.put(alignToUtcDayStart(point.time()), point.value());
    }
    return byDay.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .map(e -> new TimeValuePoint(e.getKey(), e.getValue()))
        .toList();
  }

  private List<TimeValuePoint> calculateAssetSeries(PortfolioAssetHistoryInput asset) {
    if (asset.priceSeries() == null || asset.priceSeries().points().isEmpty()) {
      return List.of();
    }
    return holdingsValueCalculator.calculateUnrounded(
        asset.priceSeries().points(),
        quantityTimelineEngine.buildQuantityTimeline(asset.transactions()));
  }

  private static final class SeriesCursor {

    private final List<TimeValuePoint> series;
    private int index;
    private BigDecimal currentValue = BigDecimal.ZERO;

    private SeriesCursor(List<TimeValuePoint> series) {
      this.series = series.stream().sorted(Comparator.comparingLong(TimeValuePoint::time)).toList();
    }

    private BigDecimal valueAt(long timestamp) {
      while (index < series.size() && series.get(index).time() <= timestamp) {
        currentValue = series.get(index).value();
        index++;
      }
      return currentValue;
    }
  }
}
