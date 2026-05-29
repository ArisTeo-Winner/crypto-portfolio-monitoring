package com.mx.cryptomonitor.marketdata.application.port.out;

import com.mx.cryptomonitor.marketdata.domain.model.StockInterval;
import com.mx.cryptomonitor.marketdata.domain.model.StockTimeSeries;

public interface StockTimeSeriesPort {

  /**
   * Obtiene una serie temporal OHLCV para el símbolo y el intervalo indicados.
   *
   * @param symbol ticker del instrumento (ej. {@code AAPL})
   * @param interval granularidad temporal del candlestick
   * @param outputSize cantidad máxima de puntos a retornar (1–5000)
   * @return serie con meta y lista de puntos ordenados ascendentemente
   */
  StockTimeSeries getTimeSeries(String symbol, StockInterval interval, int outputSize);
}
