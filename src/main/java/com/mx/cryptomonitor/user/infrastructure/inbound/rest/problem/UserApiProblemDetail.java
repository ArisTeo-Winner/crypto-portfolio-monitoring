package com.mx.cryptomonitor.user.infrastructure.inbound.rest.problem;

import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
    name = "UserApiProblemDetail",
    description = "RFC 9457 Problem Details response for user endpoints")
public record UserApiProblemDetail(
    @Schema(
            description = "Problem type URI",
            example = "https://api.cryptomonitor.com/problems/validation-error")
        String type,
    @Schema(description = "Short, human-readable summary", example = "Validation Error")
        String title,
    @Schema(description = "HTTP status code", example = "400") Integer status,
    @Schema(description = "Human-readable explanation", example = "The request is invalid")
        String detail,
    @Schema(
            description = "URI reference that identifies the specific occurrence",
            example = "/api/v1/users/register")
        String instance,
    @Schema(description = "Server timestamp in UTC", example = "2026-02-11T10:15:30Z")
        Instant timestamp,
    @Schema(description = "Stable internal error code", example = "VALIDATION_ERROR")
        String errorCode,
    @Schema(
            description = "Correlation id for troubleshooting",
            example = "a4f8e6e6-712b-4b73-9b9b-6f7b95c6ecbe")
        String traceId,
    @Schema(
            description = "Optional validation/business errors list",
            example = "[\"email: must be a well-formed email address\"]")
        List<String> errors) {}
