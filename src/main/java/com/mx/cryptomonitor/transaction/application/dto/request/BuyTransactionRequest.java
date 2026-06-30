package com.mx.cryptomonitor.transaction.application.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record BuyTransactionRequest(
    @NotBlank(message = "El simbolo del activo no puede estar vacio") String assetSymbol,
    @NotBlank(message = "El tipo de activo no puede estar vacio") String assetType,
    @NotNull(message = "La cantidad no puede ser nula")
        @Positive(message = "La cantidad debe ser mayor que cero")
        BigDecimal quantity,
    @NotNull(message = "El precio por unidad no puede ser nulo")
        @Positive(message = "El precio por unidad debe ser mayor que cero")
        BigDecimal pricePerUnit,
    @PositiveOrZero(message = "La comision no puede ser negativa") BigDecimal fee,
    @NotNull(message = "La fecha de transaccion no puede ser nula") OffsetDateTime transactionDate,
    @Size(max = 255, message = "Las notas no pueden exceder 255 caracteres") String notes,
    String assetName,
    String exchange,
    String broker,
    String currency,
    BigDecimal faceValue,
    LocalDate maturityDate,
    BigDecimal couponRate,
    Boolean autoReinvestment) {}
