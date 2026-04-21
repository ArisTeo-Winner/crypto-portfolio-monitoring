package com.mx.cryptomonitor.shared.infrastructure.problem;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;

class ApiProblemDetailsFactoryTest {

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  void createShouldUseRelativeProblemTypeRequestUriAndHeaderTraceId() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/users/register");
    request.addHeader("X-Request-Id", "req-123");

    ProblemDetail problem =
        ApiProblemDetailsFactory.create(
            HttpStatus.BAD_REQUEST,
            "validation-error",
            "Validation Error",
            "One or more fields are invalid.",
            request,
            "VALIDATION_ERROR",
            List.of("email is invalid"));

    assertThat(problem.getType().toString())
        .isEqualTo("https://api.cryptomonitor.com/problems/validation-error");
    assertThat(problem.getInstance().toString()).isEqualTo("/api/v1/users/register");
    assertThat(problem.getTitle()).isEqualTo("Validation Error");
    assertThat(problem.getStatus()).isEqualTo(400);
    assertThat(problem.getProperties()).containsEntry("errorCode", "VALIDATION_ERROR");
    assertThat(problem.getProperties()).containsEntry("traceId", "req-123");
    assertThat(problem.getProperties()).containsEntry("errors", List.of("email is invalid"));
    assertThat(problem.getProperties()).containsKey("timestamp");
  }

  @Test
  void createShouldPreserveAbsoluteProblemTypeAndDefaultErrorsList() {
    ProblemDetail problem =
        ApiProblemDetailsFactory.create(
            HttpStatus.NOT_FOUND,
            "https://api.example.com/problem/not-found",
            "Not Found",
            "Missing",
            null,
            "NOT_FOUND",
            null);

    assertThat(problem.getType().toString()).isEqualTo("https://api.example.com/problem/not-found");
    assertThat(problem.getInstance()).isNull();
    assertThat(problem.getProperties()).containsEntry("errors", List.of());
  }

  @Test
  void resolveTraceIdShouldUseMdcThenFallbackToRandomUuid() {
    MDC.put("traceId", "mdc-456");
    MockHttpServletRequest blankHeaderRequest = new MockHttpServletRequest();
    blankHeaderRequest.addHeader("X-Request-Id", "   ");

    assertThat(ApiProblemDetailsFactory.resolveTraceId(blankHeaderRequest)).isEqualTo("mdc-456");

    MDC.clear();
    String generated = ApiProblemDetailsFactory.resolveTraceId(null);

    assertThat(generated).matches("^[0-9a-fA-F-]{36}$");
  }

  @Test
  void responseFactoriesShouldSetProblemMediaTypeAndRetryAfter() {
    ProblemDetail problem =
        ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, "Slow down");

    var response = ApiProblemDetailsFactory.toProblemResponse(problem);
    var rateLimited = ApiProblemDetailsFactory.toRateLimitedResponse(problem, 60);

    assertThat(response.getHeaders().getContentType())
        .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    assertThat(rateLimited.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("60");
    assertThat(rateLimited.getHeaders().getContentType())
        .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
  }
}
