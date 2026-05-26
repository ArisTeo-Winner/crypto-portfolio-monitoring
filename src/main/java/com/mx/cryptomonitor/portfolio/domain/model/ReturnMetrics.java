package com.mx.cryptomonitor.portfolio.domain.model;

import java.math.BigDecimal;

/**
 * Immutable value object holding the return metrics for a portfolio over a given period.
 *
 * <p>{@code twr} and {@code mwr} are decimal fractions (e.g. 0.1823 = 18.23%). A value of {@code
 * null} means the metric could not be computed (e.g. insufficient data or numerical non-
 * convergence for MWR).
 */
public record ReturnMetrics(
    BigDecimal twr,
    BigDecimal mwr,
    BigDecimal absoluteGain,
    BigDecimal totalInvested) {}
