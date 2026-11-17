package com.mx.cryptomonitor.marketdata.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Cotización con procedencia (ADR-0033). {@code fetchedAt} es cuándo la obtuvo el backend; {@code
 * sessionDate} es la sesión bursátil a la que pertenece el precio y solo viene informada cuando el
 * proveedor la reporta (cierre oficial).
 */
public record StockQuote(
    BigDecimal price, Instant fetchedAt, String provider, LocalDate sessionDate) {}
