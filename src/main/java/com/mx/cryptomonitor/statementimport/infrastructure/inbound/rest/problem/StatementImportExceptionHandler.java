package com.mx.cryptomonitor.statementimport.infrastructure.inbound.rest.problem;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.mx.cryptomonitor.statementimport.domain.exception.InvalidStatementDocumentException;
import com.mx.cryptomonitor.statementimport.domain.exception.StatementImportJobNotFoundException;
import com.mx.cryptomonitor.statementimport.domain.exception.StatementImportJobNotRetryableException;
import com.mx.cryptomonitor.statementimport.domain.exception.UnrecognizedBrokerDocumentException;
import com.mx.cryptomonitor.statementimport.infrastructure.inbound.rest.StatementImportController;

@RestControllerAdvice(assignableTypes = StatementImportController.class)
public class StatementImportExceptionHandler {

  @ExceptionHandler(UnrecognizedBrokerDocumentException.class)
  ProblemDetail handleUnrecognizedDocument(UnrecognizedBrokerDocumentException ex) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    problemDetail.setTitle("Documento no reconocido");
    return problemDetail;
  }

  @ExceptionHandler(InvalidStatementDocumentException.class)
  ProblemDetail handleInvalidDocument(InvalidStatementDocumentException ex) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    problemDetail.setTitle("Documento invalido");
    return problemDetail;
  }

  @ExceptionHandler(StatementImportJobNotFoundException.class)
  ProblemDetail handleJobNotFound(StatementImportJobNotFoundException ex) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    problemDetail.setTitle("Job de importacion no encontrado");
    return problemDetail;
  }

  @ExceptionHandler(StatementImportJobNotRetryableException.class)
  ProblemDetail handleJobNotRetryable(StatementImportJobNotRetryableException ex) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    problemDetail.setTitle("Job de importacion no reintentable");
    return problemDetail;
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ProblemDetail handleRateLimitExceeded(IllegalArgumentException ex) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    problemDetail.setTitle("Limite de cargas excedido");
    return problemDetail;
  }
}
