package com.mx.cryptomonitor.user.infrastructure.inbound.rest.problem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RequestBody;

import com.mx.cryptomonitor.user.application.dto.request.UserRegistrationRequest;
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

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;

class UserExceptionHandlerTest {

  private final UserExceptionHandler handler = new UserExceptionHandler();

  @Test
  void handleValidationErrorsShouldBuildProblemDetails() throws Exception {
    BeanPropertyBindingResult bindingResult =
        new BeanPropertyBindingResult(new Object(), "userRegistrationRequest");
    bindingResult.addError(new FieldError("userRegistrationRequest", "email", "must not be blank"));
    Method method =
        DummyController.class.getDeclaredMethod("register", UserRegistrationRequest.class);
    MethodArgumentNotValidException exception =
        new MethodArgumentNotValidException(new MethodParameter(method, 0), bindingResult);
    MockHttpServletRequest request = request("/api/v1/users/register");

    var response = handler.handleValidationErrors(exception, request);

    assertProblem(response.getBody(), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
    assertThat(response.getBody().getProperties().get("errors"))
        .isEqualTo(List.of("email: must not be blank"));
  }

  @Test
  void handleConstraintViolationsShouldBuildProblemDetails() {
    @SuppressWarnings("unchecked")
    ConstraintViolation<Object> violation = mock(ConstraintViolation.class);
    Path propertyPath = mock(Path.class);
    when(propertyPath.toString()).thenReturn("register.email");
    when(violation.getPropertyPath()).thenReturn(propertyPath);
    when(violation.getMessage()).thenReturn("must be valid");
    ConstraintViolationException exception = new ConstraintViolationException(Set.of(violation));

    var response = handler.handleConstraintViolations(exception, request("/api/v1/users/register"));

    assertProblem(response.getBody(), HttpStatus.BAD_REQUEST, "VALIDATION_ERROR");
    assertThat(response.getBody().getProperties().get("errors"))
        .isEqualTo(List.of("register.email: must be valid"));
  }

  @Test
  void handleBadRequestShouldUseSafeMessageAndFallback() {
    var explicit =
        handler.handleBadRequest(
            new IllegalArgumentException("invalid email"), request("/api/v1/users/me"));
    var fallback =
        handler.handleBadRequest(new IllegalArgumentException(" "), request("/api/v1/users/me"));

    assertProblem(explicit.getBody(), HttpStatus.BAD_REQUEST, "BAD_REQUEST");
    assertThat(explicit.getBody().getDetail()).isEqualTo("invalid email");
    assertThat(fallback.getBody().getDetail()).contains("malformed");
  }

  @Test
  void handleRegistrationConflictShouldReturnConflictForBusinessAndDataIntegrityErrors() {
    var business =
        handler.handleRegistrationConflict(
            new com.mx.cryptomonitor.user.domain.exception.UserRegistrationConflictException("dup"),
            request("/api/v1/users/register"));
    var dataIntegrity =
        handler.handleRegistrationConflict(
            new DataIntegrityViolationException("db"), request("/api/v1/users/register"));

    assertProblem(business.getBody(), HttpStatus.CONFLICT, "REGISTRATION_CONFLICT");
    assertProblem(dataIntegrity.getBody(), HttpStatus.CONFLICT, "REGISTRATION_CONFLICT");
  }

  @Test
  void handleRateLimitExceptionsShouldSetRetryAfterAndExpectedCodes() {
    assertRateLimit(
        handler.handleTooManyRequests(
            new TooManyRegistrationRequestsException(10), request("/api/v1/users/register")),
        "RATE_LIMIT_EXCEEDED",
        "10");
    assertRateLimit(
        handler.handleTooManyLoginRequests(
            new TooManyLoginRequestsException(11), request("/api/v1/auth/login")),
        "LOGIN_RATE_LIMIT_EXCEEDED",
        "11");
    assertRateLimit(
        handler.handleTooManyPasswordResetRequests(
            new TooManyPasswordResetRequestsException(12), request("/api/v1/auth/password-reset")),
        "PASSWORD_RESET_RATE_LIMIT_EXCEEDED",
        "12");
    assertRateLimit(
        handler.handleTooManyPasswordChangeRequests(
            new TooManyPasswordChangeRequestsException(13), request("/api/v1/me/password")),
        "PASSWORD_CHANGE_RATE_LIMIT_EXCEEDED",
        "13");
    assertRateLimit(
        handler.handleTooManyEmailVerifyRequests(
            new TooManyEmailVerifyRequestsException(14), request("/api/v1/auth/verify-email")),
        "EMAIL_VERIFY_RATE_LIMIT_EXCEEDED",
        "14");
    assertRateLimit(
        handler.handleTooManyMeDeleteRequests(
            new TooManyMeDeleteRequestsException(15), request("/api/v1/me")),
        "ACCOUNT_DELETION_RATE_LIMIT_EXCEEDED",
        "15");
  }

  @Test
  void handleTooManyMeSoftLimitRequestsShouldDifferentiateReadAndWrite() {
    var read =
        handler.handleTooManyMeSoftLimitRequests(
            new TooManyMeReadRequestsException(), request("/api/v1/me"));
    var write =
        handler.handleTooManyMeSoftLimitRequests(
            new TooManyMeWriteRequestsException(), request("/api/v1/me"));

    assertProblem(read.getBody(), HttpStatus.TOO_MANY_REQUESTS, "ME_READ_RATE_LIMIT_EXCEEDED");
    assertThat(read.getHeaders().containsKey(HttpHeaders.RETRY_AFTER)).isFalse();
    assertProblem(write.getBody(), HttpStatus.TOO_MANY_REQUESTS, "ME_WRITE_RATE_LIMIT_EXCEEDED");
  }

  @Test
  void handleAuthenticationFailureShouldMapDifferentAuthExceptionsToUnauthorized() {
    assertProblem(
        handler
            .handleAuthenticationFailure(
                new BadCredentialsException("bad"), request("/api/v1/auth/login"))
            .getBody(),
        HttpStatus.UNAUTHORIZED,
        "UNAUTHORIZED");
    assertProblem(
        handler
            .handleAuthenticationFailure(
                new AuthenticationException("bad"), request("/api/v1/auth/login"))
            .getBody(),
        HttpStatus.UNAUTHORIZED,
        "UNAUTHORIZED");
    assertProblem(
        handler
            .handleAuthenticationFailure(
                new InvalidTokenException("bad"), request("/api/v1/auth/refresh"))
            .getBody(),
        HttpStatus.UNAUTHORIZED,
        "UNAUTHORIZED");
    assertProblem(
        handler
            .handleAuthenticationFailure(
                new ExpiredJwtException(null, null, "expired"), request("/api/v1/auth/refresh"))
            .getBody(),
        HttpStatus.UNAUTHORIZED,
        "UNAUTHORIZED");
    assertProblem(
        handler
            .handleAuthenticationFailure(
                new SignatureException("bad-signature"), request("/api/v1/auth/refresh"))
            .getBody(),
        HttpStatus.UNAUTHORIZED,
        "UNAUTHORIZED");
  }

  @Test
  void accessDeniedAndNotFoundHandlersShouldUseExpectedStatusAndFallbacks() {
    var denied =
        handler.handleAccessDenied(new AccessDeniedException("nope"), request("/api/v1/users"));
    var notFound =
        handler.handleUserNotFound(
            new UserNotFoundException("missing"), request("/api/v1/users/1"));
    var entityNotFound =
        handler.handleUserNotFound(new EntityNotFoundException(), request("/api/v1/users/1"));

    assertProblem(denied.getBody(), HttpStatus.FORBIDDEN, "FORBIDDEN");
    assertProblem(notFound.getBody(), HttpStatus.NOT_FOUND, "NOT_FOUND");
    assertThat(notFound.getBody().getDetail()).isEqualTo("missing");
    assertThat(entityNotFound.getBody().getDetail())
        .contains("requested user resource was not found");
  }

  @Test
  void handleUnhandledExceptionShouldHideInternalDetails() {
    var response =
        handler.handleUnhandledException(new RuntimeException("boom"), request("/api/v1/users"));

    assertProblem(response.getBody(), HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR");
    assertThat(response.getBody().getDetail()).contains("unexpected error occurred");
    assertThat(response.getHeaders().getContentType())
        .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
  }

  private static void assertRateLimit(
      org.springframework.http.ResponseEntity<ProblemDetail> response,
      String errorCode,
      String retryAfter) {
    assertProblem(response.getBody(), HttpStatus.TOO_MANY_REQUESTS, errorCode);
    assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo(retryAfter);
    assertThat(response.getHeaders().getContentType())
        .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
  }

  private static void assertProblem(ProblemDetail problem, HttpStatus status, String errorCode) {
    assertThat(problem).isNotNull();
    assertThat(problem.getStatus()).isEqualTo(status.value());
    assertThat(problem.getProperties()).containsEntry("errorCode", errorCode);
    assertThat(problem.getProperties()).containsKey("traceId");
    assertThat(problem.getProperties()).containsKey("timestamp");
  }

  private static MockHttpServletRequest request(String uri) {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI(uri);
    request.addHeader("X-Request-Id", "trace-123");
    return request;
  }

  static class DummyController {
    @SuppressWarnings("unused")
    void register(@RequestBody UserRegistrationRequest request) {}
  }
}
