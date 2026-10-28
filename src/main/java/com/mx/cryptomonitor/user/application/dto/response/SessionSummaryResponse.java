package com.mx.cryptomonitor.user.application.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

public record SessionSummaryResponse(
    UUID id,
    String userAgent,
    String ipAddress,
    OffsetDateTime createdAt,
    OffsetDateTime lastActiveAt,
    boolean current) {}
