package com.mx.cryptomonitor.portfolio.domain.model;

import java.util.List;

public record PortfolioHistoryResult(
    String rangeLabel,
    Resolution resolution,
    long from,
    long to,
    List<TimeValuePoint> series,
    ReturnMetrics returnMetrics,
    List<String> unavailableSymbols) {

  public PortfolioHistoryResult {
    series = series == null ? List.of() : List.copyOf(series);
    unavailableSymbols = unavailableSymbols == null ? List.of() : List.copyOf(unavailableSymbols);
  }

  /** Símbolos cuyo histórico no se pudo obtener; su ausencia hace la respuesta parcial. */
  public boolean partial() {
    return !unavailableSymbols.isEmpty();
  }

  public PortfolioHistoryResult(
      String rangeLabel,
      Resolution resolution,
      long from,
      long to,
      List<TimeValuePoint> series,
      ReturnMetrics returnMetrics) {
    this(rangeLabel, resolution, from, to, series, returnMetrics, List.of());
  }

  /** Convenience constructor for callers that do not yet compute return metrics. */
  public PortfolioHistoryResult(
      String rangeLabel, Resolution resolution, long from, long to, List<TimeValuePoint> series) {
    this(rangeLabel, resolution, from, to, series, null, List.of());
  }
}
