package com.mx.cryptomonitor.shared.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TransactionResponse(
	    UUID userId,	    
	    String assetSymbol,
		String assetType,
	    String transactionType,
	    BigDecimal quantity,
	    BigDecimal pricePerUnit,
	    BigDecimal totalValue,
	    LocalDateTime transactionDate,
	    BigDecimal fee,
	    String notes,
	    LocalDateTime createdAt,
	    LocalDateTime updatedAt
        ) {


}
