package com.mx.cryptomonitor.portfolio.infrastructure.inbound.rest.problem;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.mx.cryptomonitor.portfolio.domain.exception.InsufficientFundsException;
import com.mx.cryptomonitor.portfolio.domain.exception.MarketDataRateLimitException;
import com.mx.cryptomonitor.portfolio.domain.exception.MarketDataServerException;
import com.mx.cryptomonitor.portfolio.domain.exception.PortfolioEntryNotFoundException;
import com.mx.cryptomonitor.portfolio.domain.exception.PortfolioInvalidRequestException;
import com.mx.cryptomonitor.portfolio.domain.exception.UnknownAssetSymbolException;
import com.mx.cryptomonitor.portfolio.infrastructure.inbound.rest.PortfolioAssetHistoryController;
import com.mx.cryptomonitor.portfolio.infrastructure.inbound.rest.PortfolioChartController;
import com.mx.cryptomonitor.portfolio.infrastructure.inbound.rest.PortfolioController;
import com.mx.cryptomonitor.portfolio.infrastructure.inbound.rest.PortfolioMarkersController;
import com.mx.cryptomonitor.portfolio.infrastructure.inbound.rest.PortfolioTotalHistoryController;
import com.mx.cryptomonitor.shared.infrastructure.problem.ApiProblemDetailsFactory;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice(
    assignableTypes = {
      PortfolioController.class,
      PortfolioChartController.class,
      PortfolioAssetHistoryController.class,
      PortfolioMarkersController.class,
      PortfolioTotalHistoryController.class
    })
public class PortfolioExceptionHandler {

  @ExceptionHandler(PortfolioEntryNotFoundException.class)
  public ResponseEntity<ProblemDetail> handleNotFound(
      PortfolioEntryNotFoundException ex, HttpServletRequest request) {
    ProblemDetail problem =
        ApiProblemDetailsFactory.create(
            HttpStatus.NOT_FOUND,
            "portfolio-entry-not-found",
            "Portfolio Entry Not Found",
            ex.getMessage(),
            request,
            "PORTFOLIO_ENTRY_NOT_FOUND",
            null);
    return ApiProblemDetailsFactory.toProblemResponse(problem);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ProblemDetail> handleBadRequest(
      IllegalArgumentException ex, HttpServletRequest request) {
    ProblemDetail problem =
        ApiProblemDetailsFactory.create(
            HttpStatus.BAD_REQUEST,
            "portfolio-invalid-request",
            "Invalid Portfolio Request",
            ex.getMessage(),
            request,
            "PORTFOLIO_INVALID_REQUEST",
            null);
    return ApiProblemDetailsFactory.toProblemResponse(problem);
  }

  @ExceptionHandler(PortfolioInvalidRequestException.class)
  public ResponseEntity<ProblemDetail> handlePortfolioInvalidRequest(
      PortfolioInvalidRequestException ex, HttpServletRequest request) {
    ProblemDetail problem =
        ApiProblemDetailsFactory.create(
            HttpStatus.BAD_REQUEST,
            "portfolio-invalid-request",
            "Invalid Portfolio Request",
            ex.getMessage(),
            request,
            "PORTFOLIO_INVALID_REQUEST",
            null);
    return ApiProblemDetailsFactory.toProblemResponse(problem);
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ProblemDetail> handleForbidden(
      AccessDeniedException ex, HttpServletRequest request) {
    ProblemDetail problem =
        ApiProblemDetailsFactory.create(
            HttpStatus.FORBIDDEN,
            "portfolio-access-denied",
            "Forbidden",
            ex.getMessage(),
            request,
            "FORBIDDEN",
            null);
    return ApiProblemDetailsFactory.toProblemResponse(problem);
  }

  @ExceptionHandler(InsufficientFundsException.class)
  public ResponseEntity<ProblemDetail> handleInsufficientHoldings(
      InsufficientFundsException ex, HttpServletRequest request) {
    ProblemDetail problem =
        ApiProblemDetailsFactory.create(
            HttpStatus.CONFLICT,
            "portfolio-insufficient-holdings",
            "Insufficient Holdings",
            ex.getMessage(),
            request,
            "PORTFOLIO_INSUFFICIENT_HOLDINGS",
            null);
    return ApiProblemDetailsFactory.toProblemResponse(problem);
  }

  @ExceptionHandler(UnknownAssetSymbolException.class)
  public ResponseEntity<ProblemDetail> handleUnknownAsset(
      UnknownAssetSymbolException ex, HttpServletRequest request) {
    ProblemDetail problem =
        ApiProblemDetailsFactory.create(
            HttpStatus.NOT_FOUND,
            "portfolio-unknown-asset",
            "Unknown Asset Symbol",
            ex.getMessage(),
            request,
            "PORTFOLIO_UNKNOWN_ASSET",
            null);
    return ApiProblemDetailsFactory.toProblemResponse(problem);
  }

  @ExceptionHandler(MarketDataRateLimitException.class)
  public ResponseEntity<ProblemDetail> handleRateLimit(
      MarketDataRateLimitException ex, HttpServletRequest request) {
    ProblemDetail problem =
        ApiProblemDetailsFactory.create(
            HttpStatus.TOO_MANY_REQUESTS,
            "portfolio-market-data-rate-limit",
            "Market Data Rate Limit",
            ex.getMessage(),
            request,
            "PORTFOLIO_MARKET_DATA_RATE_LIMIT",
            null);
    return ApiProblemDetailsFactory.toProblemResponse(problem);
  }

  @ExceptionHandler(MarketDataServerException.class)
  public ResponseEntity<ProblemDetail> handleMarketDataServer(
      MarketDataServerException ex, HttpServletRequest request) {
    ProblemDetail problem =
        ApiProblemDetailsFactory.create(
            HttpStatus.BAD_GATEWAY,
            "portfolio-market-data-unavailable",
            "Market Data Unavailable",
            ex.getMessage(),
            request,
            "PORTFOLIO_MARKET_DATA_UNAVAILABLE",
            null);
    return ApiProblemDetailsFactory.toProblemResponse(problem);
  }
}
