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
    List<String> unavailableSymbols) {}
