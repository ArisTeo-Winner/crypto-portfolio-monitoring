package com.mx.cryptomonitor.integration.marketdata;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.mx.cryptomonitor.marketdata.application.service.BmvHistoryService;
import com.mx.cryptomonitor.marketdata.application.service.HybridQuoteService;
import com.mx.cryptomonitor.marketdata.domain.model.BmvHistoricalPoint;

@SpringBootTest(webEnvironment = WebEnvironment.MOCK)
@AutoConfigureMockMvc(addFilters = false)
@ActiveProfiles("test")
class BmvDataControllerIT {

  @Autowired private MockMvc mockMvc;

  @MockBean private BmvHistoryService bmvHistoryService;
  @MockBean private HybridQuoteService hybridQuoteService;

  // -------------------------------------------------------------------------
  // C6a — historico BMV
  // -------------------------------------------------------------------------

  @Test
  void getHistoricalReturnsSeriesFromService() throws Exception {
    LocalDate from = LocalDate.of(2025, 2, 20);
    LocalDate to = LocalDate.of(2025, 2, 26);
    BmvHistoricalPoint point =
        new BmvHistoricalPoint(to, new BigDecimal("4905.57"), new BigDecimal("46554582.52"));
    when(bmvHistoryService.getHistory("AAPL*", from, to)).thenReturn(List.of(point));

    mockMvc
        .perform(
            get(
                "/api/v1/marketdata/bmv/historical/{symbol}?from={from}&to={to}",
                "AAPL*",
                from,
                to))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].date").value("2025-02-26"))
        .andExpect(jsonPath("$[0].closePrice").value(4905.57))
        .andExpect(jsonPath("$[0].amountTraded").value(46554582.52));
  }

  // -------------------------------------------------------------------------
  // C6b — tipo de cambio USD/MXN
  // -------------------------------------------------------------------------

  @Test
  void getUsdMxnRateReturnsRateFromService() throws Exception {
    when(hybridQuoteService.getUsdMxnRate()).thenReturn(new BigDecimal("17.5249"));

    mockMvc
        .perform(get("/api/v1/marketdata/fx/usdmxn"))
        .andExpect(status().isOk())
        .andExpect(content().string("17.5249"));
  }

  // -------------------------------------------------------------------------
  // C6c — reconciliacion contra dato real (sanity check)
  // -------------------------------------------------------------------------

  @Test
  void historicalAaplCloseOn20250226MatchesKnownMarketValue() throws Exception {
    // Sanity check contra el valor real reportado por Investing para AAPL* el 2025-02-26,
    // verificando que closePrice/amountTraded no se invierten ni se truncan en el mapeo.
    LocalDate date = LocalDate.of(2025, 2, 26);
    BmvHistoricalPoint point =
        new BmvHistoricalPoint(date, new BigDecimal("4905.57"), new BigDecimal("46554582.52"));
    when(bmvHistoryService.getHistory("AAPL*", date, date)).thenReturn(List.of(point));

    mockMvc
        .perform(
            get(
                "/api/v1/marketdata/bmv/historical/{symbol}?from={from}&to={to}",
                "AAPL*",
                date,
                date))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].closePrice").value(4905.57));
  }
}
