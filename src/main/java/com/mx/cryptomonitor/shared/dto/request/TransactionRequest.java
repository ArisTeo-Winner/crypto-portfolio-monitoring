package com.mx.cryptomonitor.shared.dto.request;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record TransactionRequest(
		
		@NotNull(message = "El ID del usuario no puede ser nulo")
		UUID userId,
		
		UUID portfolioEntryId,
		
		@NotBlank(message = "El símbolo del activo no puede estar vacío")
        String assetSymbol, //BTC
        
        @NotBlank(message = "El tipo de activo no puede estar vacío")
        String assetType, // CRYPTO o STOCK
        
        @NotBlank(message = "El tipo de transacción no puede estar vacío")
        String transactionType, // BUY o SELL
        
        @Positive(message = "La cantidad debe ser mayor que cero")
        BigDecimal quantity,
        
        @NotNull(message = "El precio por unidad no puede ser nulo")
        BigDecimal pricePerUnit,
        
        @NotNull(message = "El valor total no puede ser nulo")
        BigDecimal totalValue,
        
        LocalDateTime transactionDate,
        
        BigDecimal fee,
        
        String notes
		) {
}
