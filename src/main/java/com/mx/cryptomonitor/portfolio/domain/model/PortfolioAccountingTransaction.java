package com.mx.cryptomonitor.portfolio.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

public record PortfolioAccountingTransaction(
    Instant time,
    String assetSymbol,
    AssetType assetType,
    String transactionType,
    BigDecimal quantity,
    BigDecimal price,
    BigDecimal grossValue,
    BigDecimal fee) {}
