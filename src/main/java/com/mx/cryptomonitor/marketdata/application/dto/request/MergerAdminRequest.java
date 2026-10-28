package com.mx.cryptomonitor.marketdata.application.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.mx.cryptomonitor.marketdata.domain.model.MergerType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Captura administrada de una fusión (ADR-0015). {@code successorSymbol} vacío en ALL_CASH; {@code
 * exchangeRatio} en ALL_STOCK/híbrido; {@code cashPerShare} en ALL_CASH/híbrido. Un {@code
 * mergerType} inválido lo rechaza Jackson como 400 al deserializar el enum.
 */
public record MergerAdminRequest(
    @NotBlank String predecessorSymbol,
    String successorSymbol,
    @NotNull MergerType mergerType,
    @PositiveOrZero BigDecimal exchangeRatio,
    @PositiveOrZero BigDecimal cashPerShare,
    String currency,
    @NotNull LocalDate effectiveDate) {}
