package com.mx.cryptomonitor.shared.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

class ErrorResponseDTOTest {

  @Test
  void builderEqualsHashCodeAndToStringShouldCoverLombokGeneratedCode() {
    LocalDateTime timestamp = LocalDateTime.of(2026, 3, 15, 5, 0);
    ErrorResponseDTO base =
        ErrorResponseDTO.builder()
            .timestamp(timestamp)
            .status(400)
            .error("Bad Request")
            .errors(List.of("email invalid"))
            .path("/api/v1/users/register")
            .build();

    ErrorResponseDTO same =
        ErrorResponseDTO.builder()
            .timestamp(timestamp)
            .status(400)
            .error("Bad Request")
            .errors(List.of("email invalid"))
            .path("/api/v1/users/register")
            .build();

    ErrorResponseDTO different =
        ErrorResponseDTO.builder()
            .timestamp(timestamp.plusMinutes(1))
            .status(409)
            .error("Conflict")
            .errors(List.of("duplicate user"))
            .path("/api/v1/users")
            .build();

    assertThat(base).isEqualTo(same);
    assertThat(base.hashCode()).isEqualTo(same.hashCode());
    assertThat(base).isNotEqualTo(different);
    assertThat(base).isNotEqualTo(null);
    assertThat(base).isNotEqualTo(new Object());
    assertThat(base.toString()).contains("Bad Request").contains("/api/v1/users/register");

    base.setStatus(401);
    base.setError("Unauthorized");
    base.setErrors(List.of());
    base.setPath("/api/v1/auth/login");

    assertThat(base.getStatus()).isEqualTo(401);
    assertThat(base.getError()).isEqualTo("Unauthorized");
    assertThat(base.getErrors()).isEmpty();
    assertThat(base.getPath()).isEqualTo("/api/v1/auth/login");
  }
}
