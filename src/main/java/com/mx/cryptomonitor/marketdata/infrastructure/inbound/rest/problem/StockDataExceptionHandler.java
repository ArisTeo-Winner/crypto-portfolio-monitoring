package com.mx.cryptomonitor.marketdata.infrastructure.inbound.rest.problem;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.mx.cryptomonitor.marketdata.domain.exception.ExternalProviderInvalidSymbolException;
import com.mx.cryptomonitor.marketdata.domain.exception.ExternalProviderRateLimitException;
import com.mx.cryptomonitor.marketdata.domain.exception.ExternalProviderUpstreamException;
import com.mx.cryptomonitor.marketdata.infrastructure.inbound.rest.StockDataController;
import com.mx.cryptomonitor.shared.infrastructure.problem.ApiProblemDetailsFactory;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice(assignableTypes = StockDataController.class)
public class StockDataExceptionHandler {

  @ExceptionHandler(ExternalProviderInvalidSymbolException.class)
  public ResponseEntity<ProblemDetail> handleInvalidSymbol(
      ExternalProviderInvalidSymbolException ex, HttpServletRequest request) {
    ProblemDetail problem =
        ApiProblemDetailsFactory.create(
            HttpStatus.BAD_REQUEST,
            "invalid-stock-symbol",
            "Invalid Stock Symbol",
            ex.getMessage(),
            request,
            "INVALID_STOCK_SYMBOL",
            null);
    return ApiProblemDetailsFactory.toProblemResponse(problem);
  }

  @ExceptionHandler(ExternalProviderRateLimitException.class)
  public ResponseEntity<ProblemDetail> handleRateLimit(
      ExternalProviderRateLimitException ex, HttpServletRequest request) {
    ProblemDetail problem =
        ApiProblemDetailsFactory.create(
            HttpStatus.TOO_MANY_REQUESTS,
            "upstream-rate-limited",
            "Upstream Rate Limited",
            ex.getMessage(),
            request,
            "STOCK_PROVIDER_RATE_LIMITED",
            null);
    return ApiProblemDetailsFactory.toProblemResponse(problem);
  }

  @ExceptionHandler(ExternalProviderUpstreamException.class)
  public ResponseEntity<ProblemDetail> handleServerError(
      ExternalProviderUpstreamException ex, HttpServletRequest request) {
    ProblemDetail problem =
        ApiProblemDetailsFactory.create(
            HttpStatus.BAD_GATEWAY,
            "upstream-error",
            "Upstream Error",
            ex.getMessage(),
            request,
            "STOCK_PROVIDER_UPSTREAM_ERROR",
            null);
    return ApiProblemDetailsFactory.toProblemResponse(problem);
  }
}
