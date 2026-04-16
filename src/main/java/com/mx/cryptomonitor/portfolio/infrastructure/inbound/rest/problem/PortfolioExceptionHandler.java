package com.mx.cryptomonitor.portfolio.infrastructure.inbound.rest.problem;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.mx.cryptomonitor.portfolio.domain.exception.PortfolioEntryNotFoundException;
import com.mx.cryptomonitor.portfolio.infrastructure.inbound.rest.PortfolioController;
import com.mx.cryptomonitor.shared.infrastructure.problem.ApiProblemDetailsFactory;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice(assignableTypes = PortfolioController.class)
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
}
