package com.mx.cryptomonitor.marketdata.application.port.out;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Proyeccion de una transaccion del modulo transaction relevante para valuacion de bonos. */
public record GovBondPositionView(
    UUID transactionId,
    String assetSymbol,
    String assetType,
    String currency,
    BigDecimal quantity,
    BigDecimal totalValue,
    BigDecimal couponRate,
    LocalDate maturityDate,
    BigDecimal faceValue) {}
