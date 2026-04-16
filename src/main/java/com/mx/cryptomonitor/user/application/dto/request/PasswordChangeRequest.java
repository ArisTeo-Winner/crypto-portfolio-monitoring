package com.mx.cryptomonitor.user.application.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PasswordChangeRequest(
    @NotBlank(message = "Current password is mandatory") String currentPassword,
    @NotBlank(message = "New password is mandatory")
        @Size(min = 8, message = "Password must be at least 8 characters long")
        @Pattern(
            regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!]).{8,}$",
            message =
                "Password must be strong (use uppercase, lowercase, numbers, and special characters)")
        String newPassword) {

  /**
   * Avoid leaking secrets via logs. Some Spring components may log request objects using {@code
   * toString()} when debug logging is enabled.
   */
  @Override
  public String toString() {
    return "PasswordChangeRequest[credentials=<redacted>]";
  }
}
