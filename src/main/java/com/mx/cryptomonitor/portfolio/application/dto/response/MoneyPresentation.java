package com.mx.cryptomonitor.portfolio.application.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Metadata de presentacion de moneda (ADR-0010). Los importes del DTO ya vienen en {@code
 * displayCurrency}; este envelope declara la base, la moneda mostrada y la tasa USD->display
 * aplicada ({@code 1} cuando display=USD), mas su procedencia ({@code rateProvider}/{@code
 * rateAsOf}, nulos cuando no se aplico FX). Permite al cliente formatear y auditar la conversion.
 */
public record MoneyPresentation(
    String baseCurrency,
    String displayCurrency,
    BigDecimal fxRate,
    String rateProvider,
    OffsetDateTime rateAsOf) {}
