package com.mx.cryptomonitor.shared.infrastructure.inbound.rest;

import java.time.Instant;

import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/health")
@RequiredArgsConstructor
@Tag(name = "Health", description = "Public operational health endpoint")
public class HealthController {

  private static final String SERVICE_NAME = "crypto-portfolio-monitoring";

  private final HealthEndpoint healthEndpoint;

  @Operation(
      summary = "Public health check",
      description = "Returns a sanitized health status suitable for load balancers and uptime checks.")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Application is healthy",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = HealthResponse.class))),
        @ApiResponse(
            responseCode = "503",
            description = "Application is not ready or a critical dependency is down",
            content =
                @Content(
                    mediaType = "application/json",
                    schema = @Schema(implementation = HealthResponse.class)))
      })
  @GetMapping
  public ResponseEntity<HealthResponse> health() {
    HealthComponent health = healthEndpoint.health();
    HttpStatus httpStatus = Status.UP.equals(health.getStatus()) ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;

    return ResponseEntity.status(httpStatus)
        .body(new HealthResponse(health.getStatus().getCode(), SERVICE_NAME, Instant.now()));
  }

  public record HealthResponse(String status, String service, Instant timestamp) {}
}
