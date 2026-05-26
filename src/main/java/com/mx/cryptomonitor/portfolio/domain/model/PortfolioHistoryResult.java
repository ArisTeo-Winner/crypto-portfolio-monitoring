package com.mx.cryptomonitor.portfolio.domain.model;

import java.util.List;

public record PortfolioHistoryResult(
    String rangeLabel,
    Resolution resolution,
    long from,
    long to,
    List<TimeValuePoint> series,
    ReturnMetrics returnMetrics) {

  public PortfolioHistoryResult {
    series = series == null ? List.of() : List.copyOf(series);
  }

  /** Convenience constructor for callers that do not yet compute return metrics. */
  public PortfolioHistoryResult(
      String rangeLabel, Resolution resolution, long from, long to, List<TimeValuePoint> series) {
    this(rangeLabel, resolution, from, to, series, null);
  }
}
