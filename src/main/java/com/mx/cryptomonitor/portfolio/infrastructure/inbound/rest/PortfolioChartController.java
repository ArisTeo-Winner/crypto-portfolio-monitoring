package com.mx.cryptomonitor.portfolio.infrastructure.inbound.rest;

import java.util.List;
import java.util.UUID;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mx.cryptomonitor.portfolio.application.dto.response.PortfolioChartPointResponse;
import com.mx.cryptomonitor.portfolio.application.dto.response.PortfolioMarkerResponse;
import com.mx.cryptomonitor.portfolio.application.port.in.PortfolioChartPort;
import com.mx.cryptomonitor.portfolio.infrastructure.inbound.rest.security.PortfolioHistoryRateLimiter;
import com.mx.cryptomonitor.user.application.port.in.CurrentUserPort;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/portfolio")
@RequiredArgsConstructor
public class PortfolioChartController {

  private final PortfolioChartPort portfolioChartPort;
  private final CurrentUserPort currentUserPort;
  private final PortfolioHistoryRateLimiter portfolioHistoryRateLimiter;

  @Operation(summary = "Obtener curva de equity del portfolio autenticado")
  @GetMapping("/history")
  @PreAuthorize("hasRole('USER')")
  public List<PortfolioChartPointResponse> getHistory(
      @RequestParam(defaultValue = "180") String range,
      Authentication authentication,
      HttpServletRequest request) {
    portfolioHistoryRateLimiter.validate(request);
    UUID userId = currentUserPort.resolveUserId(authentication);
    return portfolioChartPort.getEquityHistory(userId, range);
  }

  @Operation(summary = "Obtener marcadores BUY/SELL del portfolio autenticado")
  @GetMapping("/markers")
  @PreAuthorize("hasRole('USER')")
  public List<PortfolioMarkerResponse> getMarkers(
      @RequestParam(defaultValue = "180") String range,
      Authentication authentication,
      HttpServletRequest request) {
    portfolioHistoryRateLimiter.validate(request);
    UUID userId = currentUserPort.resolveUserId(authentication);
    return portfolioChartPort.getMarkers(userId, range);
  }

  @Operation(summary = "Obtener PnL realizado del portfolio autenticado")
  @GetMapping("/realized")
  @PreAuthorize("hasRole('USER')")
  public List<PortfolioChartPointResponse> getRealizedPnl(
      @RequestParam(defaultValue = "180") String range,
      Authentication authentication,
      HttpServletRequest request) {
    portfolioHistoryRateLimiter.validate(request);
    UUID userId = currentUserPort.resolveUserId(authentication);
    return portfolioChartPort.getRealizedPnl(userId, range);
  }
}
