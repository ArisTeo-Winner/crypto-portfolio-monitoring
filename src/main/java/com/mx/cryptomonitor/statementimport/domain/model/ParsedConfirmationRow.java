package com.mx.cryptomonitor.statementimport.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ParsedConfirmationRow(
    String symbol,
    String securityName,
    String action,
    BigDecimal quantity,
    BigDecimal price,
    LocalDate tradeDate,
    BigDecimal commission,
    BigDecimal netAmount) {}
