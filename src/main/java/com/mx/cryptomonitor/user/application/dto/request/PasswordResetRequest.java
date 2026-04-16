package com.mx.cryptomonitor.user.application.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record PasswordResetRequest(
    @Email(message = "Email should be valid") @NotBlank(message = "Email is mandatory")
        String email) {}
