package com.mx.cryptomonitor.transaction.application.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.mx.cryptomonitor.transaction.domain.model.AssetType;

public record TransactionDetailsResponse(
    UUID id,
    String assetSymbol,
    AssetType assetType,
    String transactionType,
    String transferType,
    OffsetDateTime transactionDate,
    BigDecimal quantity,
    BigDecimal pricePerUnit,
    BigDecimal grossAmount,
    BigDecimal fee,
    String feeCurrency,
    BigDecimal netAmount,
    String amountLabel,
    String notes,
    String source,
    String exchange,
    String status,
    FrictionBreakdownView frictionBreakdown) {}
