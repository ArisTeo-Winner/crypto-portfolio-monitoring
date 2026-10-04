package com.mx.cryptomonitor.portfolio.application.dto.request;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Entrada del preview de split (ADR-0011, flujo de captura). El usuario teclea
 * fecha/ticker/cantidad/ precio y el sistema calcula —sin guardar nada— si hubo un split entre esa
 * fecha y hoy y como se veria la operacion ya ajustada. {@code quantity}/{@code pricePerUnit} son
 * opcionales: si faltan, solo se reporta la deteccion del split sin calcular montos ajustados.
 */
public record SplitPreviewRequest(
    @NotBlank String symbol,
    @NotNull OffsetDateTime transactionDate,
    BigDecimal quantity,
    BigDecimal pricePerUnit) {}
