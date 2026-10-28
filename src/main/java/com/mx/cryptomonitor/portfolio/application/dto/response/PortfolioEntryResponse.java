package com.mx.cryptomonitor.portfolio.application.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record PortfolioEntryResponse(
    UUID portfolioEntryId,
    UUID userId,
    String assetSymbol,
    String assetType,
    BigDecimal totalQuantity,
    BigDecimal totalInvested,
    BigDecimal averagePricePerUnit,
    BigDecimal lastTransactionPrice,
    BigDecimal currentValue,
    BigDecimal totalProfitLoss,
    LocalDateTime lastUpdated,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    MoneyPresentation presentation) {

  /**
   * Compat: proyeccion base sin envelope de presentacion (ADR-0010) => {@code presentation=null}.
   */
  public PortfolioEntryResponse(
      UUID portfolioEntryId,
      UUID userId,
      String assetSymbol,
      String assetType,
      BigDecimal totalQuantity,
      BigDecimal totalInvested,
      BigDecimal averagePricePerUnit,
      BigDecimal lastTransactionPrice,
      BigDecimal currentValue,
      BigDecimal totalProfitLoss,
      LocalDateTime lastUpdated,
      LocalDateTime createdAt,
      LocalDateTime updatedAt) {
    this(
        portfolioEntryId,
        userId,
        assetSymbol,
        assetType,
        totalQuantity,
        totalInvested,
        averagePricePerUnit,
        lastTransactionPrice,
        currentValue,
        totalProfitLoss,
        lastUpdated,
        createdAt,
        updatedAt,
        null);
  }
}
