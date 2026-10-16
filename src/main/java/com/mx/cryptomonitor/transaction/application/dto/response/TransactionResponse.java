package com.mx.cryptomonitor.transaction.application.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    // ADR-0013: binomio del evento de operacion. El front debe renderizar en la zona de la plaza
    // (executionZoneId) con etiqueta; executedAtUtc es el instante para ordenar/convertir.
    LocalDateTime localExecutionTime,
    String executionZoneId,
    OffsetDateTime executedAtUtc,
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
