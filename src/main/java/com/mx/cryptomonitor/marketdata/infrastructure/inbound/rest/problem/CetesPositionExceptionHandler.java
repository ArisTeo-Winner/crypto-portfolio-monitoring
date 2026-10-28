package com.mx.cryptomonitor.marketdata.infrastructure.inbound.rest.problem;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.mx.cryptomonitor.marketdata.domain.exception.GovBondPositionNotFoundException;
import com.mx.cryptomonitor.marketdata.domain.exception.InvalidGovBondPositionException;
import com.mx.cryptomonitor.marketdata.infrastructure.inbound.rest.CetesPositionController;
import com.mx.cryptomonitor.shared.infrastructure.problem.ApiProblemDetailsFactory;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice(assignableTypes = CetesPositionController.class)
public class CetesPositionExceptionHandler {

  @ExceptionHandler(GovBondPositionNotFoundException.class)
  public ResponseEntity<ProblemDetail> handleNotFound(
      GovBondPositionNotFoundException ex, HttpServletRequest request) {
    ProblemDetail problem =
        ApiProblemDetailsFactory.create(
            HttpStatus.NOT_FOUND,
            "gov-bond-position-not-found",
            "Transaction Not Found",
            ex.getMessage(),
            request,
            "GOV_BOND_POSITION_NOT_FOUND",
            null);
    return ApiProblemDetailsFactory.toProblemResponse(problem);
  }

  @ExceptionHandler(InvalidGovBondPositionException.class)
  public ResponseEntity<ProblemDetail> handleInvalid(
      InvalidGovBondPositionException ex, HttpServletRequest request) {
    ProblemDetail problem =
        ApiProblemDetailsFactory.create(
            HttpStatus.BAD_REQUEST,
            "invalid-gov-bond-position",
            "Invalid Government Bond Position",
            ex.getMessage(),
            request,
            "INVALID_GOV_BOND_POSITION",
            null);
    return ApiProblemDetailsFactory.toProblemResponse(problem);
  }
}
