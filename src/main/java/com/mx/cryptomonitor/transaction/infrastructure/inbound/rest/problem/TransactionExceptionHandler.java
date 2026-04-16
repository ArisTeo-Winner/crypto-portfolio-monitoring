package com.mx.cryptomonitor.transaction.infrastructure.inbound.rest.problem;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.mx.cryptomonitor.transaction.domain.exception.IdempotencyConflictException;
import com.mx.cryptomonitor.transaction.domain.exception.InvalidTransactionException;
import com.mx.cryptomonitor.transaction.domain.exception.TransactionNotFoundException;
import com.mx.cryptomonitor.transaction.infrastructure.inbound.rest.TransactionController;

@RestControllerAdvice(assignableTypes = TransactionController.class)
public class TransactionExceptionHandler {

  @ExceptionHandler(TransactionNotFoundException.class)
  ProblemDetail handleTransactionNotFound(TransactionNotFoundException ex) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    problemDetail.setTitle("Transaction Not Found");
    return problemDetail;
  }

  @ExceptionHandler(InvalidTransactionException.class)
  ProblemDetail handleInvalidTransaction(InvalidTransactionException ex) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    problemDetail.setTitle("Invalid Transaction");
    return problemDetail;
  }

  @ExceptionHandler(IdempotencyConflictException.class)
  ProblemDetail handleIdempotencyConflict(IdempotencyConflictException ex) {
    ProblemDetail problemDetail =
        ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    problemDetail.setTitle("Idempotency Conflict");
    return problemDetail;
  }
}
