package com.mx.cryptomonitor.marketdata.application.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.mx.cryptomonitor.marketdata.application.port.out.MarketDataProvider;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StockDataService {

  private final MarketDataProvider marketDataProvider;

  public Optional<BigDecimal> getStockQuote(String symbol) {
    return marketDataProvider.getLatest(symbol);
  }

  public Optional<BigDecimal> getHistoricalStockPrice(String symbol, LocalDate date) {
    return marketDataProvider.getHistorical(symbol, date);
  }
}
