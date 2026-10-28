package com.mx.cryptomonitor.portfolio.application.dto.response;

import java.math.BigDecimal;

/**
 * Return-metrics payload embedded in {@link PortfolioHistoryMeta}.
 *
 * <p>All decimal fields are fractions (e.g. {@code 0.1823} = 18.23 %). A {@code null} value means
 * the metric could not be computed (insufficient history or numerical non-convergence).
 */
public record ReturnMetricsResponse(
    BigDecimal twr, BigDecimal mwr, BigDecimal absoluteGain, BigDecimal totalInvested) {}
