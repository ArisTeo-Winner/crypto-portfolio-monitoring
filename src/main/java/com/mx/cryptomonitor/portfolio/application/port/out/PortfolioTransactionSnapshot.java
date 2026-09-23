package com.mx.cryptomonitor.portfolio.application.port.out;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PortfolioTransactionSnapshot(
    String assetSymbol,
    String assetType,
    String transactionType,
    String transferType,
    BigDecimal quantity,
    BigDecimal totalValue,
    BigDecimal pricePerUnit,
    BigDecimal fee,
    BigDecimal realizedPnl,
    OffsetDateTime transactionDate,
    String currency) {

  /** Compat: sin {@code currency} (queda null => sin conversion FX). */
  public PortfolioTransactionSnapshot(
      String assetSymbol,
      String assetType,
      String transactionType,
      String transferType,
      BigDecimal quantity,
      BigDecimal totalValue,
      BigDecimal pricePerUnit,
      BigDecimal fee,
      BigDecimal realizedPnl,
      OffsetDateTime transactionDate) {
    this(
        assetSymbol,
        assetType,
        transactionType,
        transferType,
        quantity,
        totalValue,
        pricePerUnit,
        fee,
        realizedPnl,
        transactionDate,
        null);
  }

  /** Compat: sin {@code realizedPnl} ni {@code currency}. */
  public PortfolioTransactionSnapshot(
      String assetSymbol,
      String assetType,
      String transactionType,
      String transferType,
      BigDecimal quantity,
      BigDecimal totalValue,
      BigDecimal pricePerUnit,
      BigDecimal fee,
      OffsetDateTime transactionDate) {
    this(
        assetSymbol,
        assetType,
        transactionType,
        transferType,
        quantity,
        totalValue,
        pricePerUnit,
        fee,
        BigDecimal.ZERO,
        transactionDate,
        null);
  }
}
