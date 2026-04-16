package com.mx.cryptomonitor.user.application.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @Email(message = "Email should be valid") @NotBlank(message = "Email is mandatory")
        String email,
    @NotBlank(message = "Password is mandatory") String password) {

  /**
   * Avoid leaking secrets via logs. Some Spring components may log request objects using {@code
   * toString()} when debug logging is enabled.
   */
  @Override
  public String toString() {
    return "LoginRequest[email=" + email + ", secret=<redacted>]";
  }
}
