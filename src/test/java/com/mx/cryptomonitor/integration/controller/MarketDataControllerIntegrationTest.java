package com.mx.cryptomonitor.integration.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.mx.cryptomonitor.marketdata.application.port.out.MarketDataProvider;

@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class MarketDataControllerIntegrationTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private CacheManager cacheManager;

  @MockBean private MarketDataProvider marketDataProvider;

  @BeforeEach
  void setup() {
    cacheManager.getCache("historicalPrices").clear();
  }

  @Test
  void getHistoricalPrice_returnsCorrectValue() throws Exception {

    String symbol = "AAPL";
    LocalDate date = LocalDate.of(2025, 11, 25);
    when(marketDataProvider.getHistorical(symbol, date))
        .thenReturn(Optional.of(new BigDecimal("276.9700")));

    mockMvc
        .perform(
            get("/api/v1/marketdata/stock/historical/{symbol}/{date}", symbol, date)
                .contentType(MediaType.APPLICATION_JSON))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(content().string("276.9700"));
  }
}
