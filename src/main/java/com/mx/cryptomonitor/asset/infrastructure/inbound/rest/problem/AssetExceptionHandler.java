package com.mx.cryptomonitor.asset.infrastructure.inbound.rest.problem;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.mx.cryptomonitor.asset.domain.exception.TooManyAssetSearchRequestsException;
import com.mx.cryptomonitor.asset.infrastructure.inbound.rest.AssetController;
import com.mx.cryptomonitor.shared.infrastructure.problem.ApiProblemDetailsFactory;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice(assignableTypes = AssetController.class)
public class AssetExceptionHandler {

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ProblemDetail> handleInvalidParams(
      IllegalArgumentException ex, HttpServletRequest request) {
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

  @ExceptionHandler(TooManyAssetSearchRequestsException.class)
  public ResponseEntity<ProblemDetail> handleRateLimited(
      TooManyAssetSearchRequestsException ex, HttpServletRequest request) {
    ProblemDetail problem =
        ApiProblemDetailsFactory.create(
            HttpStatus.TOO_MANY_REQUESTS,
            "rate-limited",
            "Too Many Requests",
            "Too many asset search requests. Please try again later.",
            request,
            "ASSET_SEARCH_RATE_LIMIT_EXCEEDED",
            null);
    return ApiProblemDetailsFactory.toRateLimitedResponse(problem, ex.getRetryAfterSeconds());
  }
}
