package com.mx.cryptomonitor.shared.infrastructure.problem;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

import jakarta.servlet.http.HttpServletRequest;

public final class ApiProblemDetailsFactory {

  private static final String PROBLEM_BASE_URI = "https://api.cryptomonitor.com/problems/";
  private static final String REQUEST_ID_HEADER = "X-Request-Id";

  private ApiProblemDetailsFactory() {}

  public static ProblemDetail create(
      HttpStatus status,
      String problemType,
      String title,
      String detail,
      HttpServletRequest request,
      String errorCode,
      List<String> errors) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setTitle(title);
    problem.setType(URI.create(resolveProblemType(problemType)));
    if (request != null) {
      problem.setInstance(URI.create(request.getRequestURI()));
    }
    problem.setProperty("timestamp", Instant.now());
    problem.setProperty("errorCode", errorCode);
    problem.setProperty("traceId", resolveTraceId(request));
    problem.setProperty("errors", errors == null ? List.of() : errors);
    return problem;
  }

  public static ResponseEntity<ProblemDetail> toProblemResponse(ProblemDetail problem) {
    return ResponseEntity.status(problem.getStatus())
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .body(problem);
  }

  public static ResponseEntity<ProblemDetail> toRateLimitedResponse(
      ProblemDetail problem, long retryAfterSeconds) {
    return ResponseEntity.status(problem.getStatus())
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds))
        .body(problem);
  }

  public static String resolveTraceId(HttpServletRequest request) {
    return Optional.ofNullable(request)
        .map(value -> value.getHeader(REQUEST_ID_HEADER))
        .filter(header -> !header.isBlank())
        .or(() -> Optional.ofNullable(MDC.get("traceId")).filter(value -> !value.isBlank()))
        .orElseGet(() -> UUID.randomUUID().toString());
  }

  private static String resolveProblemType(String problemType) {
    if (problemType.startsWith("http://") || problemType.startsWith("https://")) {
      return problemType;
    }
    return PROBLEM_BASE_URI + problemType;
  }
}
