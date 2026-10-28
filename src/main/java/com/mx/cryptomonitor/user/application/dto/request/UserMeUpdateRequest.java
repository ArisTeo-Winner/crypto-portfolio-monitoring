package com.mx.cryptomonitor.user.application.dto.request;

import java.time.LocalDate;

import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Size;

public record UserMeUpdateRequest(
    @Size(max = 120, message = "firstName too long") String firstName,
    @Size(max = 120, message = "lastName too long") String lastName,
    @Size(max = 30, message = "phoneNumber too long") String phoneNumber,
    @Size(max = 200, message = "address too long") String address,
    @Size(max = 120, message = "city too long") String city,
    @Size(max = 120, message = "state too long") String state,
    @Size(max = 30, message = "postalCode too long") String postalCode,
    @Size(max = 120, message = "country too long") String country,
    @Size(max = 500, message = "bio too long") String bio,
    @Size(max = 3, message = "preferredCurrency must be ISO 4217 code (3 chars)")
        String preferredCurrency,
    @Size(max = 50, message = "timezone too long") String timezone,
    @Past(message = "dateOfBirth must be a past date") LocalDate dateOfBirth) {}
