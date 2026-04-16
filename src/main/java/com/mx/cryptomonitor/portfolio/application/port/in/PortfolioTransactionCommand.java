package com.mx.cryptomonitor.portfolio.application.port.in;

import java.math.BigDecimal;

public record PortfolioTransactionCommand(
    String assetSymbol,
    String assetType,
    String transactionType,
    String transferType,
    BigDecimal quantity,
    BigDecimal totalValue,
    BigDecimal pricePerUnit) {}
