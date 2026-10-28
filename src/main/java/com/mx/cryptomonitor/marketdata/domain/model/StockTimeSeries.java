package com.mx.cryptomonitor.marketdata.domain.model;

import java.util.List;

/**
 * Serie temporal de precios OHLCV para un símbolo de stock.
 *
 * <p>Incluye los metadatos que devuelve Twelve Data en el campo {@code meta} de la respuesta, más
 * la lista de puntos ordenada cronológicamente (ascendente).
 */
public record StockTimeSeries(
    String symbol,
    StockInterval interval,
    String currency,
    String exchange,
    String exchangeTimezone,
    String type,
    List<StockTimeSeriesPoint> values) {}
