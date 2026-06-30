package com.mx.cryptomonitor.transaction.application.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TransactionResponse(
    UUID transactionId,
    String assetSymbol,
    String assetType,
    String transactionType,
    BigDecimal quantity,
    BigDecimal pricePerUnit,
    BigDecimal totalValue,
    OffsetDateTime transactionDate,
    BigDecimal fee,
    String notes,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    String logoUrl,
    String assetName,
    String exchange,
    String broker,
    String currency,
    BigDecimal faceValue,
    LocalDate maturityDate,
    BigDecimal couponRate,
    Boolean autoReinvestment) {}
