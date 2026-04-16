package com.mx.cryptomonitor.transaction.application.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record TransactionResponse(
    UUID transactionId,
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
    LocalDateTime updatedAt) {}
