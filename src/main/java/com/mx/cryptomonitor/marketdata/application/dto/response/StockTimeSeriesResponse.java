package com.mx.cryptomonitor.marketdata.application.dto.response;

import java.math.BigDecimal;
import java.util.List;

import com.mx.cryptomonitor.marketdata.domain.model.StockTimeSeries;

/**
 * DTO de respuesta para el endpoint {@code GET /api/v1/marketdata/stock/time-series}.
 *
 * <p>Refleja la estructura de Twelve Data añadiendo el campo {@code count} para facilitar
 * la validación en Postman.
 */
public record StockTimeSeriesResponse(
    String symbol,
    String interval,
    String currency,
    String exchange,
    String exchangeTimezone,
    String type,
    int count,
    List<PointDto> values) {

  public record PointDto(
      String datetime,
      BigDecimal open,
      BigDecimal high,
      BigDecimal low,
      BigDecimal close,
      Long volume) {}

  /** Construye el DTO desde el agregado de dominio. */
  public static StockTimeSeriesResponse from(StockTimeSeries series) {
    List<PointDto> points =
        series.values().stream()
            .map(
                p ->
                    new PointDto(
                        p.datetime(), p.open(), p.high(), p.low(), p.close(), p.volume()))
            .toList();

    return new StockTimeSeriesResponse(
        series.symbol(),
        series.interval().getApiCode(),
        series.currency(),
        series.exchange(),
        series.exchangeTimezone(),
        series.type(),
        points.size(),
        points);
  }
}
