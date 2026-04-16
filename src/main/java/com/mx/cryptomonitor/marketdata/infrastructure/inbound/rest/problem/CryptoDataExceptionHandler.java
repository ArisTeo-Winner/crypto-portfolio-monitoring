package com.mx.cryptomonitor.marketdata.infrastructure.inbound.rest.problem;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.mx.cryptomonitor.marketdata.domain.exception.CoinMarketCapInvalidParamException;
import com.mx.cryptomonitor.marketdata.domain.exception.CoinMarketCapRateLimitException;
import com.mx.cryptomonitor.marketdata.domain.exception.CoinMarketCapServerException;
import com.mx.cryptomonitor.marketdata.domain.exception.TooManyCryptoPriceRequestsException;
import com.mx.cryptomonitor.marketdata.infrastructure.inbound.rest.CryptoDataController;
import com.mx.cryptomonitor.shared.infrastructure.problem.ApiProblemDetailsFactory;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice(assignableTypes = CryptoDataController.class)
public class CryptoDataExceptionHandler {

  @ExceptionHandler(CoinMarketCapInvalidParamException.class)
  public ResponseEntity<ProblemDetail> handleInvalidParams(
      CoinMarketCapInvalidParamException ex, HttpServletRequest request) {
    ProblemDetail problem =
        ApiProblemDetailsFactory.create(
            HttpStatus.BAD_REQUEST,
            "invalid-parameters",
            "Invalid Parameters",
            ex.getMessage(),
            request,
            "VALIDATION_ERROR",
            null);
    return ApiProblemDetailsFactory.toProblemResponse(problem);
  }

  @ExceptionHandler(CoinMarketCapRateLimitException.class)
  public ResponseEntity<ProblemDetail> handleRateLimit(
      CoinMarketCapRateLimitException ex, HttpServletRequest request) {
    ProblemDetail problem =
        ApiProblemDetailsFactory.create(
            HttpStatus.TOO_MANY_REQUESTS,
            "rate-limited",
            "Too Many Requests",
            ex.getMessage(),
            request,
            "UPSTREAM_RATE_LIMIT_EXCEEDED",
            null);
    return ApiProblemDetailsFactory.toProblemResponse(problem);
  }

  @ExceptionHandler(TooManyCryptoPriceRequestsException.class)
  public ResponseEntity<ProblemDetail> handleLocalRateLimit(
      TooManyCryptoPriceRequestsException ex, HttpServletRequest request) {
    ProblemDetail problem =
        ApiProblemDetailsFactory.create(
            HttpStatus.TOO_MANY_REQUESTS,
            "rate-limited",
            "Too Many Requests",
            "Too many crypto quote requests. Please try again later.",
            request,
            "CRYPTO_PRICE_RATE_LIMIT_EXCEEDED",
            null);
    return ApiProblemDetailsFactory.toRateLimitedResponse(problem, ex.getRetryAfterSeconds());
  }

  @ExceptionHandler(CoinMarketCapServerException.class)
  public ResponseEntity<ProblemDetail> handleUpstreamServerError(
      CoinMarketCapServerException ex, HttpServletRequest request) {
    ProblemDetail problem =
        ApiProblemDetailsFactory.create(
            HttpStatus.BAD_GATEWAY,
            "upstream-error",
            "Upstream Error",
            ex.getMessage(),
            request,
            "UPSTREAM_ERROR",
            null);
    return ApiProblemDetailsFactory.toProblemResponse(problem);
  }
}
