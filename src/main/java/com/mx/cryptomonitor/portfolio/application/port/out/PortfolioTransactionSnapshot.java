package com.mx.cryptomonitor.portfolio.application.port.out;

import java.math.BigDecimal;
import java.time.LocalDateTime;

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
    LocalDateTime transactionDate) {

  public PortfolioTransactionSnapshot(
      String assetSymbol,
      String assetType,
      String transactionType,
      String transferType,
      BigDecimal quantity,
      BigDecimal totalValue,
      BigDecimal pricePerUnit,
      BigDecimal fee,
      LocalDateTime transactionDate) {
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
        transactionDate);
  }
}
