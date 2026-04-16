package com.mx.cryptomonitor.shared.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CmcStatus(
    Instant timestamp,
    Integer error_code,
    String error_message,
    String elapsed,
    String credit_count) {}
