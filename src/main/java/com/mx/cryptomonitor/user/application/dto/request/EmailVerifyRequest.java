package com.mx.cryptomonitor.user.application.dto.request;

import jakarta.validation.constraints.NotBlank;

public record EmailVerifyRequest(
    @NotBlank(message = "Verification token is mandatory") String token) {

  /**
   * Avoid leaking secrets via logs. Some Spring components may log request objects using {@code
   * toString()} when debug logging is enabled.
   */
  @Override
  public String toString() {
    return "EmailVerifyRequest[token=<redacted>]";
  }
}
