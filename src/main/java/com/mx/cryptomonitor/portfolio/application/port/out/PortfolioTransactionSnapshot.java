package com.mx.cryptomonitor.portfolio.application.port.out;

import java.math.BigDecimal;
import java.time.LocalDate;
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
    String currency,
    BigDecimal taxWithheld,
    // Campos de renta fija (ADR-0018): necesarios para valuar por devengo un bono a descuento.
    // null en activos que no son bonos; el valuador de devengo se salta la posicion si quedan null.
    LocalDate maturityDate,
    BigDecimal faceValue,
    BigDecimal couponRate) {

  /** Compat: sin campos de bono (maturityDate/faceValue/couponRate => null). */
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
      OffsetDateTime transactionDate,
      String currency,
      BigDecimal taxWithheld) {
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
        currency,
        taxWithheld,
        null,
        null,
        null);
  }

  /** Compat: sin {@code taxWithheld} (queda null => sin retencion de dividendo, ADR-0014 F4). */
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
      OffsetDateTime transactionDate,
      String currency) {
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
        currency,
        null);
  }

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
        null,
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
        null,
        null);
  }
}
