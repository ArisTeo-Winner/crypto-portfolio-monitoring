package com.mx.cryptomonitor.marketdata.infrastructure.inbound.rest.problem;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.mx.cryptomonitor.marketdata.domain.exception.TooManyMarketStatusRequestsException;
import com.mx.cryptomonitor.marketdata.infrastructure.inbound.rest.MarketStatusController;
import com.mx.cryptomonitor.shared.infrastructure.problem.ApiProblemDetailsFactory;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice(assignableTypes = MarketStatusController.class)
public class MarketStatusExceptionHandler {

  @ExceptionHandler(TooManyMarketStatusRequestsException.class)
  public ResponseEntity<ProblemDetail> handleLocalRateLimit(
      TooManyMarketStatusRequestsException ex, HttpServletRequest request) {
    ProblemDetail problem =
        ApiProblemDetailsFactory.create(
            HttpStatus.TOO_MANY_REQUESTS,
            "rate-limited",
            "Too Many Requests",
            "Too many market status requests. Please try again later.",
            request,
            "MARKET_STATUS_RATE_LIMIT_EXCEEDED",
            null);
    return ApiProblemDetailsFactory.toRateLimitedResponse(problem, ex.getRetryAfterSeconds());
  }
}
