package com.mx.cryptomonitor.unit.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.test.web.servlet.MockMvc;

import com.mx.cryptomonitor.marketdata.application.service.StockDataService;
import com.mx.cryptomonitor.marketdata.domain.exception.AlphaVantageRateLimitException;
import com.mx.cryptomonitor.marketdata.domain.exception.AlphaVantageServerException;
import com.mx.cryptomonitor.marketdata.infrastructure.inbound.rest.StockDataController;
import com.mx.cryptomonitor.user.application.service.AuthenticationService;
import com.mx.cryptomonitor.user.application.service.JwtUserDetailsService;
import com.mx.cryptomonitor.user.domain.repository.SessionRepository;
import com.mx.cryptomonitor.user.domain.repository.UserRepository;
import com.mx.cryptomonitor.user.infrastructure.security.JwtAuthenticationEntryPoint;
import com.mx.cryptomonitor.user.infrastructure.security.JwtTokenUtil;

@WebMvcTest(StockDataController.class)
@AutoConfigureMockMvc(addFilters = false)
class StockDataControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockBean private StockDataService stockDataService;
  @MockBean private AuthenticationManager authenticationManager;
  @MockBean private JwtTokenUtil jwtTokenUtil;
  @MockBean private JwtUserDetailsService jwtUserDetailsService;
  @MockBean private AuthenticationService authenticationService;
  @MockBean private JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
  @MockBean private UserRepository userRepository;
  @MockBean private SessionRepository sessionRepository;

  @Test
  void getStockPriceShouldReturnOkWhenQuoteExists() throws Exception {
    when(stockDataService.getStockQuote("AMZN"))
        .thenReturn(Optional.of(new BigDecimal("233.1400")));

    mockMvc
        .perform(get("/api/v1/marketdata/stock").param("symbol", "AMZN"))
        .andExpect(status().isOk())
        .andExpect(content().string("233.1400"));
  }

  @Test
  void getStockPriceShouldReturnNotFoundWhenQuoteMissing() throws Exception {
    when(stockDataService.getStockQuote("INVALID")).thenReturn(Optional.empty());

    mockMvc
        .perform(get("/api/v1/marketdata/stock").param("symbol", "INVALID"))
        .andExpect(status().isNotFound())
        .andExpect(content().string("No se encontro informacion para el simbolo: INVALID"));
  }

  @Test
  void getStockPriceShouldReturnTooManyRequestsWhenAlphaVantageRateLimits() throws Exception {
    when(stockDataService.getStockQuote("GOOG"))
        .thenThrow(new AlphaVantageRateLimitException("Thank you for using Alpha Vantage!"));

    mockMvc
        .perform(get("/api/v1/marketdata/stock").param("symbol", "GOOG"))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.errorCode").value("STOCK_PROVIDER_RATE_LIMITED"));
  }

  @Test
  void getStockPriceShouldReturnBadGatewayWhenAlphaVantageFails() throws Exception {
    when(stockDataService.getStockQuote("GOOG"))
        .thenThrow(new AlphaVantageServerException("Temporary upstream error"));

    mockMvc
        .perform(get("/api/v1/marketdata/stock").param("symbol", "GOOG"))
        .andExpect(status().isBadGateway())
        .andExpect(jsonPath("$.errorCode").value("STOCK_PROVIDER_UPSTREAM_ERROR"));
  }

  @Test
  void getStockPriceShouldReturnBadRequestWhenSymbolIsBlank() throws Exception {
    mockMvc
        .perform(get("/api/v1/marketdata/stock").param("symbol", " "))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getHistoricalStockPriceShouldReturnOkWhenHistoricalQuoteExists() throws Exception {
    when(stockDataService.getHistoricalStockPrice("AAPL", LocalDate.parse("2025-11-25")))
        .thenReturn(Optional.of(new BigDecimal("201.3400")));

    mockMvc
        .perform(get("/api/v1/marketdata/stock/historical/AAPL/2025-11-25"))
        .andExpect(status().isOk())
        .andExpect(content().string("201.3400"));
  }

  @Test
  void getHistoricalStockPriceShouldReturnBadRequestWhenDateHasInvalidFormat() throws Exception {
    mockMvc
        .perform(get("/api/v1/marketdata/stock/historical/AAPL/not-a-date"))
        .andExpect(status().isBadRequest())
        .andExpect(content().string("La fecha debe tener el formato yyyy-MM-dd."));
  }

  @Test
  void getHistoricalStockPriceShouldReturnNotFoundWhenHistoricalQuoteMissing() throws Exception {
    when(stockDataService.getHistoricalStockPrice("AAPL", LocalDate.parse("2025-11-25")))
        .thenReturn(Optional.empty());

    mockMvc
        .perform(get("/api/v1/marketdata/stock/historical/AAPL/2025-11-25"))
        .andExpect(status().isNotFound())
        .andExpect(
            content()
                .string(
                    "No se encontro informacion historica para el simbolo: AAPL en la fecha: 2025-11-25"));
  }

  @Test
  void getStockPriceShouldReturnBadRequestWhenSymbolIsNull() {
    StockDataController controller = new StockDataController(stockDataService);

    var response = controller.getStockPrice(null);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isEqualTo("El simbolo de la accion no debe ser nulo ni vacio.");
  }

  @Test
  void getHistoricalStockPriceShouldReturnBadRequestWhenSymbolIsBlank() {
    StockDataController controller = new StockDataController(stockDataService);

    var response = controller.getHistoricalStockPrice(" ", "2025-11-25");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isEqualTo("El simbolo de la accion no debe ser nulo ni vacio.");
  }

  @Test
  void getHistoricalStockPriceShouldReturnBadRequestWhenDateIsBlank() {
    StockDataController controller = new StockDataController(stockDataService);

    var response = controller.getHistoricalStockPrice("AAPL", " ");

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).isEqualTo("La fecha no debe ser nula ni vacia.");
  }
}
