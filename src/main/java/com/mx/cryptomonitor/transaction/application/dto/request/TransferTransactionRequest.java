package com.mx.cryptomonitor.transaction.application.dto.request;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record TransferTransactionRequest(
    @NotBlank(message = "El simbolo del activo no puede estar vacio") String assetSymbol,
    @NotBlank(message = "El tipo de activo no puede estar vacio") String assetType,
    @NotBlank(message = "El tipo de transferencia no puede estar vacio")
        @Pattern(
            regexp = "TRANSFER_IN|TRANSFER_OUT",
            message = "El tipo de transferencia debe ser TRANSFER_IN o TRANSFER_OUT")
        String transferType,
    @NotNull(message = "La cantidad no puede ser nula")
        @Positive(message = "La cantidad debe ser mayor que cero")
        BigDecimal quantity,
    @PositiveOrZero(message = "La comision no puede ser negativa") BigDecimal fee,
    @NotNull(message = "La fecha de transaccion no puede ser nula") LocalDateTime transactionDate,
    @Size(max = 255, message = "Las notas no pueden exceder 255 caracteres") String notes) {}
