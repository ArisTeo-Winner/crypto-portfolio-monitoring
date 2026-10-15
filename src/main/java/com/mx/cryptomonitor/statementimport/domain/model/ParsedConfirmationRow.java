package com.mx.cryptomonitor.statementimport.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

public record ParsedConfirmationRow(
    String symbol,
    String securityName,
    String action,
    BigDecimal quantity,
    BigDecimal price,
    LocalDate tradeDate,
    LocalTime executionTime,
    BigDecimal principalAmount,
    BigDecimal commission,
    BigDecimal transactionFee,
    BigDecimal otherFees,
    BigDecimal netAmount) {}
