package com.mx.cryptomonitor.portfolio.domain.model;

import java.util.List;

public record PortfolioHistoryResult(
    String rangeLabel, Resolution resolution, long from, long to, List<TimeValuePoint> series) {

  public PortfolioHistoryResult {
    series = series == null ? List.of() : List.copyOf(series);
  }
}
