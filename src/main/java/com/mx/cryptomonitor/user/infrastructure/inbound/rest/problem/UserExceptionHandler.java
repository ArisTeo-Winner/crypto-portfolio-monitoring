package com.mx.cryptomonitor.user.infrastructure.inbound.rest.problem;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.mx.cryptomonitor.user.domain.exception.AuthenticationException;
import com.mx.cryptomonitor.user.domain.exception.InvalidTokenException;
import com.mx.cryptomonitor.user.domain.exception.TooManyEmailVerifyRequestsException;
import com.mx.cryptomonitor.user.domain.exception.TooManyLoginRequestsException;
import com.mx.cryptomonitor.user.domain.exception.TooManyMeDeleteRequestsException;
import com.mx.cryptomonitor.user.domain.exception.TooManyMeReadRequestsException;
import com.mx.cryptomonitor.user.domain.exception.TooManyMeWriteRequestsException;
import com.mx.cryptomonitor.user.domain.exception.TooManyPasswordChangeRequestsException;
import com.mx.cryptomonitor.user.domain.exception.TooManyPasswordResetRequestsException;
import com.mx.cryptomonitor.user.domain.exception.TooManyRegistrationRequestsException;
import com.mx.cryptomonitor.user.domain.exception.UserNotFoundException;
import com.mx.cryptomonitor.user.domain.exception.UserRegistrationConflictException;
import com.mx.cryptomonitor.user.infrastructure.inbound.rest.UserController;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;

@RestControllerAdvice(assignableTypes = UserController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class UserExceptionHandler {

  private static final String GENERIC_SERVER_DETAIL =
      "An unexpected error occurred. Contact support with the traceId.";
  private static final String GENERIC_REGISTRATION_CONFLICT_DETAIL =
      "Registration cannot be completed with the provided data.";
  private static final String GENERIC_RATE_LIMIT_DETAIL =
      "Too many requests. Please try again later.";

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ProblemDetail> handleValidationErrors(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    List<String> errors =
        ex.getBindingResult().getFieldErrors().stream()
            .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
            .collect(Collectors.toList());

    ProblemDetail problem =
        UserProblemDetailsFactory.create(
            HttpStatus.BAD_REQUEST,
            "validation-error",
            "Validation Error",
            "One or more fields are invalid.",
            request,
            "VALIDATION_ERROR",
            errors);
    return toProblemResponse(problem);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ProblemDetail> handleConstraintViolations(
      ConstraintViolationException ex, HttpServletRequest request) {
    List<String> errors =
        ex.getConstraintViolations().stream()
            .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
            .collect(Collectors.toList());

    ProblemDetail problem =
        UserProblemDetailsFactory.create(
            HttpStatus.BAD_REQUEST,
            "validation-error",
            "Validation Error",
            "Request constraints were violated.",
            request,
            "VALIDATION_ERROR",
            errors);
    return toProblemResponse(problem);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ProblemDetail> handleBadRequest(
      IllegalArgumentException ex, HttpServletRequest request) {
    log.warn("Bad request in user endpoint at {}: {}", request.getRequestURI(), ex.getMessage());
    ProblemDetail problem =
        UserProblemDetailsFactory.create(
            HttpStatus.BAD_REQUEST,
            "bad-request",
            "Bad Request",
            safeMessage(ex, "The request is malformed or contains invalid parameters."),
            request,
            "BAD_REQUEST",
            List.of());
    return toProblemResponse(problem);
  }

  @ExceptionHandler({
    UserRegistrationConflictException.class,
    DataIntegrityViolationException.class
  })
  public ResponseEntity<ProblemDetail> handleRegistrationConflict(
      Exception ex, HttpServletRequest request) {
    log.warn(
        "Registration conflict in user endpoint at {}: {}",
        request.getRequestURI(),
        ex.getClass().getSimpleName());
    ProblemDetail problem =
        UserProblemDetailsFactory.create(
            HttpStatus.CONFLICT,
            "conflict",
            "Conflict",
            GENERIC_REGISTRATION_CONFLICT_DETAIL,
            request,
            "REGISTRATION_CONFLICT",
            List.of());
    return toProblemResponse(problem);
  }

  @ExceptionHandler(TooManyRegistrationRequestsException.class)
  public ResponseEntity<ProblemDetail> handleTooManyRequests(
      TooManyRegistrationRequestsException ex, HttpServletRequest request) {
    log.warn("Rate limit exceeded in user endpoint at {}", request.getRequestURI());
    ProblemDetail problem =
        UserProblemDetailsFactory.create(
            HttpStatus.TOO_MANY_REQUESTS,
            "rate-limited",
            "Too Many Requests",
            GENERIC_RATE_LIMIT_DETAIL,
            request,
            "RATE_LIMIT_EXCEEDED",
            List.of());
    return ResponseEntity.status(problem.getStatus())
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
        .body(problem);
  }

  @ExceptionHandler(TooManyLoginRequestsException.class)
  public ResponseEntity<ProblemDetail> handleTooManyLoginRequests(
      TooManyLoginRequestsException ex, HttpServletRequest request) {
    log.warn("Login rate limit exceeded in user endpoint at {}", request.getRequestURI());
    ProblemDetail problem =
        UserProblemDetailsFactory.create(
            HttpStatus.TOO_MANY_REQUESTS,
            "rate-limited",
            "Too Many Requests",
            GENERIC_RATE_LIMIT_DETAIL,
            request,
            "LOGIN_RATE_LIMIT_EXCEEDED",
            List.of());
    return ResponseEntity.status(problem.getStatus())
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
        .body(problem);
  }

  @ExceptionHandler(TooManyPasswordResetRequestsException.class)
  public ResponseEntity<ProblemDetail> handleTooManyPasswordResetRequests(
      TooManyPasswordResetRequestsException ex, HttpServletRequest request) {
    log.warn("Password reset rate limit exceeded in user endpoint at {}", request.getRequestURI());
    ProblemDetail problem =
        UserProblemDetailsFactory.create(
            HttpStatus.TOO_MANY_REQUESTS,
            "rate-limited",
            "Too Many Requests",
            GENERIC_RATE_LIMIT_DETAIL,
            request,
            "PASSWORD_RESET_RATE_LIMIT_EXCEEDED",
            List.of());
    return ResponseEntity.status(problem.getStatus())
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
        .body(problem);
  }

  @ExceptionHandler(TooManyPasswordChangeRequestsException.class)
  public ResponseEntity<ProblemDetail> handleTooManyPasswordChangeRequests(
      TooManyPasswordChangeRequestsException ex, HttpServletRequest request) {
    log.warn("Password change rate limit exceeded in user endpoint at {}", request.getRequestURI());
    ProblemDetail problem =
        UserProblemDetailsFactory.create(
            HttpStatus.TOO_MANY_REQUESTS,
            "rate-limited",
            "Too Many Requests",
            GENERIC_RATE_LIMIT_DETAIL,
            request,
            "PASSWORD_CHANGE_RATE_LIMIT_EXCEEDED",
            List.of());
    return ResponseEntity.status(problem.getStatus())
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
        .body(problem);
  }

  @ExceptionHandler(TooManyEmailVerifyRequestsException.class)
  public ResponseEntity<ProblemDetail> handleTooManyEmailVerifyRequests(
      TooManyEmailVerifyRequestsException ex, HttpServletRequest request) {
    log.warn("Email verify rate limit exceeded in user endpoint at {}", request.getRequestURI());
    ProblemDetail problem =
        UserProblemDetailsFactory.create(
            HttpStatus.TOO_MANY_REQUESTS,
            "rate-limited",
            "Too Many Requests",
            GENERIC_RATE_LIMIT_DETAIL,
            request,
            "EMAIL_VERIFY_RATE_LIMIT_EXCEEDED",
            List.of());
    return ResponseEntity.status(problem.getStatus())
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
        .body(problem);
  }

  @ExceptionHandler(TooManyMeDeleteRequestsException.class)
  public ResponseEntity<ProblemDetail> handleTooManyMeDeleteRequests(
      TooManyMeDeleteRequestsException ex, HttpServletRequest request) {
    log.error("ADMIN_ALERT: account deletion rate limit exceeded at {}", request.getRequestURI());
    ProblemDetail problem =
        UserProblemDetailsFactory.create(
            HttpStatus.TOO_MANY_REQUESTS,
            "rate-limited",
            "Too Many Requests",
            GENERIC_RATE_LIMIT_DETAIL,
            request,
            "ACCOUNT_DELETION_RATE_LIMIT_EXCEEDED",
            List.of());
    return ResponseEntity.status(problem.getStatus())
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
        .body(problem);
  }

  @ExceptionHandler({TooManyMeReadRequestsException.class, TooManyMeWriteRequestsException.class})
  public ResponseEntity<ProblemDetail> handleTooManyMeSoftLimitRequests(
      RuntimeException ex, HttpServletRequest request) {
    log.warn("Soft rate limit exceeded in user endpoint at {}", request.getRequestURI());
    String errorCode =
        ex instanceof TooManyMeWriteRequestsException
            ? "ME_WRITE_RATE_LIMIT_EXCEEDED"
            : "ME_READ_RATE_LIMIT_EXCEEDED";
    ProblemDetail problem =
        UserProblemDetailsFactory.create(
            HttpStatus.TOO_MANY_REQUESTS,
            "rate-limited",
            "Too Many Requests",
            GENERIC_RATE_LIMIT_DETAIL,
            request,
            errorCode,
            List.of());
    return ResponseEntity.status(problem.getStatus())
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problem);
  }

  @ExceptionHandler({
    BadCredentialsException.class,
    AuthenticationException.class,
    org.springframework.security.core.AuthenticationException.class,
    InvalidTokenException.class,
    ExpiredJwtException.class,
    SignatureException.class
  })
  public ResponseEntity<ProblemDetail> handleAuthenticationFailure(
      Exception ex, HttpServletRequest request) {
    log.warn(
        "Authentication failure in user endpoint at {}: {}",
        request.getRequestURI(),
        ex.getMessage());
    ProblemDetail problem =
        UserProblemDetailsFactory.create(
            HttpStatus.UNAUTHORIZED,
            "unauthorized",
            "Unauthorized",
            "Authentication is required or token is invalid.",
            request,
            "UNAUTHORIZED",
            List.of());
    return toProblemResponse(problem);
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ProblemDetail> handleAccessDenied(
      AccessDeniedException ex, HttpServletRequest request) {
    log.warn("Access denied in user endpoint at {}: {}", request.getRequestURI(), ex.getMessage());
    ProblemDetail problem =
        UserProblemDetailsFactory.create(
            HttpStatus.FORBIDDEN,
            "forbidden",
            "Forbidden",
            "You do not have permission to access this resource.",
            request,
            "FORBIDDEN",
            List.of());
    return toProblemResponse(problem);
  }

  @ExceptionHandler({UserNotFoundException.class, EntityNotFoundException.class})
  public ResponseEntity<ProblemDetail> handleUserNotFound(
      Exception ex, HttpServletRequest request) {
    log.warn("User resource not found at {}: {}", request.getRequestURI(), ex.getMessage());
    ProblemDetail problem =
        UserProblemDetailsFactory.create(
            HttpStatus.NOT_FOUND,
            "not-found",
            "Resource Not Found",
            safeMessage(ex, "The requested user resource was not found."),
            request,
            "NOT_FOUND",
            List.of());
    return toProblemResponse(problem);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> handleUnhandledException(
      Exception ex, HttpServletRequest request) {
    log.error(
        "Unhandled exception in user endpoint at {}: {}",
        request.getRequestURI(),
        ex.getMessage(),
        ex);
    ProblemDetail problem =
        UserProblemDetailsFactory.create(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "internal-error",
            "Internal Server Error",
            GENERIC_SERVER_DETAIL,
            request,
            "INTERNAL_ERROR",
            List.of());
    return toProblemResponse(problem);
  }

  private ResponseEntity<ProblemDetail> toProblemResponse(ProblemDetail problem) {
    return ResponseEntity.status(problem.getStatus())
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problem);
  }

  private String safeMessage(Exception ex, String fallback) {
    if (ex.getMessage() == null || ex.getMessage().isBlank()) {
      return fallback;
    }
    return ex.getMessage();
  }
}
