package com.mx.cryptomonitor.user.application.dto.request;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UserRegistrationRequest(
    @NotBlank(message = "Username is mandatory")
        @Size(min = 4, max = 50, message = "Username must be between 4 and 50 characters")
        @Pattern(
            regexp = "^[a-zA-Z0-9_]+$",
            message = "Username can only contain letters, numbers and underscores")
        String username,
    @Email(message = "Email should be valid") @NotBlank(message = "Email is mandatory")
        String email,
    @NotBlank(message = "Password is mandatory")
        @Size(min = 8, message = "Password must be at least 8 characters long")
        // Complexity requirement: At least 1 upper, 1 lower, 1 digit, 1 special char
        @Pattern(
            regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!]).{8,}$",
            message =
                "Password must be strong (use uppercase, lowercase, numbers, and special characters)")
        String password,
    @Size(max = 60, message = "First name must be at most 60 characters")
        @Pattern(
            regexp = "^[\\p{L}\\p{M}' .\\-]{1,60}$",
            message = "First name contains invalid characters")
        String firstName,
    @Size(max = 60, message = "Last name must be at most 60 characters")
        @Pattern(
            regexp = "^[\\p{L}\\p{M}' .\\-]{1,60}$",
            message = "Last name contains invalid characters")
        String lastName,
    String phoneNumber,
    String address,
    String city,
    String state,
    String postalCode,
    String country,
    LocalDate dateOfBirth,
    String preferredCurrency,
    String timezone) {

  /**
   * Avoid leaking secrets via logs. Some Spring components may log request objects using {@code
   * toString()} when debug logging is enabled.
   */
  @Override
  public String toString() {
    return "UserRegistrationRequest[username="
        + username
        + ", email="
        + email
        + ", credentials=<redacted>]";
  }
}
