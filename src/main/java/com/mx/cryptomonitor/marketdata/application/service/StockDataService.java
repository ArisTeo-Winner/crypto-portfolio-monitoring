package com.mx.cryptomonitor.marketdata.application.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.mx.cryptomonitor.marketdata.application.port.out.MarketDataProvider;
import com.mx.cryptomonitor.marketdata.application.port.out.StockTimeSeriesPort;
import com.mx.cryptomonitor.marketdata.domain.model.StockInterval;
import com.mx.cryptomonitor.marketdata.domain.model.StockTimeSeries;

/**
 * Servicio de aplicación para datos de mercado de stocks.
 *
 * <p>{@code timeSeriesPort} se inyecta como {@link Optional} porque depende del bean {@code
 * TwelveDataAdapter}, que solo se registra cuando {@code TWELVEDATA_API_KEY} está configurada. Si
 * el bean no existe, los métodos que lo usan retornan {@code Optional.empty()}.
 */
@Service
public class StockDataService {

  private final MarketDataProvider marketDataProvider;
  private final Optional<StockTimeSeriesPort> timeSeriesPort;

  @Autowired
  public StockDataService(
      MarketDataProvider marketDataProvider, Optional<StockTimeSeriesPort> timeSeriesPort) {
    this.marketDataProvider = marketDataProvider;
    this.timeSeriesPort = timeSeriesPort;
  }

  public Optional<BigDecimal> getStockQuote(String symbol) {
    return marketDataProvider.getLatest(symbol);
  }

  public Optional<BigDecimal> getHistoricalStockPrice(String symbol, LocalDate date) {
    return marketDataProvider.getHistorical(symbol, date);
  }

  /**
   * Obtiene una serie temporal OHLCV con el intervalo solicitado.
   *
   * <p>El resultado se almacena en Redis (cache {@code stockTimeSeries}, TTL 1 hora). La primera
   * llamada consulta Twelve Data; las siguientes se sirven desde el caché sin consumir cuota de
   * API. Se usa {@code unless = "#result?.isEmpty() == true"} para no cachear el caso en que el
   * proveedor no está configurado ({@code Optional.empty()}).
   *
   * <p>La clave incluye {@code symbol + interval + outputSize} porque distintos {@code outputSize}
   * producen series con distinto número de puntos.
   *
   * @return {@link Optional#empty()} si Twelve Data no está configurado
   */
  @Cacheable(
      cacheNames = "stockTimeSeries",
      key = "#symbol.toUpperCase() + ':' + #interval.apiCode + ':' + #outputSize",
      unless = "#result == null || #result.isEmpty()")
  public Optional<StockTimeSeries> getTimeSeries(
      String symbol, StockInterval interval, int outputSize) {
    return timeSeriesPort.map(port -> port.getTimeSeries(symbol, interval, outputSize));
  }
}
