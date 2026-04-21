package com.mx.cryptomonitor.unit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mx.cryptomonitor.marketdata.application.port.out.MarketDataProvider;
import com.mx.cryptomonitor.marketdata.application.service.StockDataService;

@ExtendWith(MockitoExtension.class)
class StockDataServiceTest {

  @Mock private MarketDataProvider marketDataProvider;

  @InjectMocks private StockDataService stockDataService;

  @Test
  void getStockQuoteShouldDelegateToProvider() {
    when(marketDataProvider.getLatest("AAPL")).thenReturn(Optional.of(new BigDecimal("195.21")));

    Optional<BigDecimal> result = stockDataService.getStockQuote("AAPL");

    assertThat(result).contains(new BigDecimal("195.21"));
    verify(marketDataProvider).getLatest("AAPL");
  }

  @Test
  void getHistoricalStockPriceShouldDelegateToProvider() {
    LocalDate date = LocalDate.parse("2025-11-25");
    when(marketDataProvider.getHistorical("AAPL", date))
        .thenReturn(Optional.of(new BigDecimal("201.34")));

    Optional<BigDecimal> result = stockDataService.getHistoricalStockPrice("AAPL", date);

    assertThat(result).contains(new BigDecimal("201.34"));
    verify(marketDataProvider).getHistorical("AAPL", date);
  }
}
