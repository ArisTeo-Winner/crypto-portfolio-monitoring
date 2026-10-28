package com.mx.cryptomonitor.transaction.application.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record DividendTransactionRequest(
    @NotBlank String assetSymbol,
    String assetName,
    @NotBlank String assetType,
    @NotNull @Positive BigDecimal amount,
    @NotNull OffsetDateTime transactionDate,
    LocalDate exDividendDate,
    String dividendType,
    BigDecimal taxWithheld,
    String broker,
    String currency,
    String exchange) {}
