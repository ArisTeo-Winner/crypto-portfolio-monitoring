package com.mx.cryptomonitor.portfolio.application.dto.response;

public record PortfolioHistoryMeta(
    String range,
    String resolution,
    long from,
    long to,
    String currency,
    int points,
    ReturnMetricsResponse returns) {}
