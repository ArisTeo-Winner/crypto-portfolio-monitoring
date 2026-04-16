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
    LocalDateTime transactionDate) {}
