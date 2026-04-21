package com.mx.cryptomonitor.unit.asset.infrastructure.inbound.rest.problem;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import com.mx.cryptomonitor.asset.domain.exception.TooManyAssetSearchRequestsException;
import com.mx.cryptomonitor.asset.infrastructure.inbound.rest.problem.AssetExceptionHandler;

class AssetExceptionHandlerTest {

  private final AssetExceptionHandler handler = new AssetExceptionHandler();

  @Test
  void handleInvalidParamsShouldBuildProblemDetailsResponse() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/assets/search");
    request.addHeader("X-Request-Id", "req-123");

    ResponseEntity<ProblemDetail> response =
        handler.handleInvalidParams(
            new IllegalArgumentException("Query parameter q is required."), request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getHeaders().getContentType())
        .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getTitle()).isEqualTo("Invalid Parameters");
    assertThat(response.getBody().getDetail()).isEqualTo("Query parameter q is required.");
    assertThat(response.getBody().getType().toString())
        .isEqualTo("https://api.cryptomonitor.com/problems/invalid-parameters");
    assertThat(response.getBody().getInstance().toString()).isEqualTo("/api/v1/assets/search");
    assertThat(response.getBody().getProperties()).containsEntry("errorCode", "VALIDATION_ERROR");
    assertThat(response.getBody().getProperties()).containsEntry("traceId", "req-123");
    assertThat(response.getBody().getProperties()).containsKey("timestamp");
    assertThat(response.getBody().getProperties()).containsEntry("errors", java.util.List.of());
  }

  @Test
  void handleRateLimitedShouldBuildProblemDetailsResponseWithRetryAfter() {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.setRequestURI("/api/v1/assets/search");

    ResponseEntity<ProblemDetail> response =
        handler.handleRateLimited(new TooManyAssetSearchRequestsException(42L), request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    assertThat(response.getHeaders().getContentType())
        .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
    assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("42");
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().getTitle()).isEqualTo("Too Many Requests");
    assertThat(response.getBody().getDetail())
        .isEqualTo("Too many asset search requests. Please try again later.");
    assertThat(response.getBody().getType().toString())
        .isEqualTo("https://api.cryptomonitor.com/problems/rate-limited");
    assertThat(response.getBody().getProperties())
        .containsEntry("errorCode", "ASSET_SEARCH_RATE_LIMIT_EXCEEDED");
  }
}
