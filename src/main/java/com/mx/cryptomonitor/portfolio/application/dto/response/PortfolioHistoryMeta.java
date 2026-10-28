package com.mx.cryptomonitor.portfolio.application.dto.response;

import java.util.List;

public record PortfolioHistoryMeta(
    String range,
    String resolution,
    long from,
    long to,
    String currency,
    int points,
    ReturnMetricsResponse returns,
    boolean partial,
    List<String> unavailableSymbols,
    MoneyPresentation presentation) {

  /** Compat: sin envelope de presentacion (ADR-0010) => {@code presentation=null}. */
  public PortfolioHistoryMeta(
      String range,
      String resolution,
      long from,
      long to,
      String currency,
      int points,
      ReturnMetricsResponse returns,
      boolean partial,
      List<String> unavailableSymbols) {
    this(range, resolution, from, to, currency, points, returns, partial, unavailableSymbols, null);
  }
}
