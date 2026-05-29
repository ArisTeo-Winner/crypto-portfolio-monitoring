package com.mx.cryptomonitor.marketdata.domain.model;

import java.math.BigDecimal;

/**
 * Punto OHLCV de una serie temporal de stock.
 *
 * <p>{@code datetime} se conserva como {@link String} tal como lo devuelve Twelve Data: {@code
 * "2026-05-22"} para intervalos diarios y {@code "2026-05-22 15:30:00"} para intraday. El timezone
 * del exchange se incluye en {@link StockTimeSeries} a nivel de serie.
 */
public record StockTimeSeriesPoint(
    String datetime,
    BigDecimal open,
    BigDecimal high,
    BigDecimal low,
    BigDecimal close,
    Long volume) {}
