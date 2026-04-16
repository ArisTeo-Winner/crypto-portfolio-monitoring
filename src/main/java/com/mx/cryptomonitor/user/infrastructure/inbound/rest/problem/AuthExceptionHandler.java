package com.mx.cryptomonitor.user.infrastructure.inbound.rest.problem;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.mx.cryptomonitor.shared.infrastructure.problem.ApiProblemDetailsFactory;
import com.mx.cryptomonitor.user.domain.exception.AuthenticationException;
import com.mx.cryptomonitor.user.domain.exception.InvalidTokenException;
import com.mx.cryptomonitor.user.domain.exception.SessionNotFoundException;
import com.mx.cryptomonitor.user.domain.exception.TooManyLoginRequestsException;
import com.mx.cryptomonitor.user.domain.exception.UserNotFoundException;
import com.mx.cryptomonitor.user.infrastructure.inbound.rest.AuthController;
import com.mx.cryptomonitor.user.infrastructure.inbound.rest.TokenController;

import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice(assignableTypes = {AuthController.class, TokenController.class})
public class AuthExceptionHandler {

  private static final String GENERIC_SERVER_DETAIL =
      "An unexpected error occurred. Contact support with the traceId.";

  @ExceptionHandler(TooManyLoginRequestsException.class)
  public ResponseEntity<ProblemDetail> handleTooManyLoginRequests(
      TooManyLoginRequestsException ex, HttpServletRequest request) {
    ProblemDetail problem =
        ApiProblemDetailsFactory.create(
            HttpStatus.TOO_MANY_REQUESTS,
            "rate-limited",
            "Too Many Requests",
            "Too many login attempts. Please try again later.",
            request,
            "LOGIN_RATE_LIMIT_EXCEEDED",
            null);

    return ApiProblemDetailsFactory.toRateLimitedResponse(problem, ex.getRetryAfterSeconds());
  }

  @ExceptionHandler({
    AuthenticationException.class,
    BadCredentialsException.class,
    org.springframework.security.core.AuthenticationException.class
  })
  public ResponseEntity<ProblemDetail> handleAuthenticationFailure(HttpServletRequest request) {
    ProblemDetail problem =
        ApiProblemDetailsFactory.create(
            HttpStatus.UNAUTHORIZED,
            "unauthorized",
            "Unauthorized",
            "Authentication is required or credentials are invalid.",
            request,
            "UNAUTHORIZED",
            null);
    return ApiProblemDetailsFactory.toProblemResponse(problem);
  }

  @ExceptionHandler({
    InvalidTokenException.class,
    SessionNotFoundException.class,
    UserNotFoundException.class
  })
  public ResponseEntity<ProblemDetail> handleInvalidRefreshOrSession(HttpServletRequest request) {
    ProblemDetail problem =
        ApiProblemDetailsFactory.create(
            HttpStatus.UNAUTHORIZED,
            "unauthorized",
            "Unauthorized",
            "Authentication is required or token is invalid.",
            request,
            "UNAUTHORIZED",
            null);
    return ApiProblemDetailsFactory.toProblemResponse(problem);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> handleUnhandledException(HttpServletRequest request) {
    ProblemDetail problem =
        ApiProblemDetailsFactory.create(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "internal-error",
            "Internal Server Error",
            GENERIC_SERVER_DETAIL,
            request,
            "INTERNAL_ERROR",
            null);
    return ApiProblemDetailsFactory.toProblemResponse(problem);
  }
}
