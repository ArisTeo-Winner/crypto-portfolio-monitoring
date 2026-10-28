package com.mx.cryptomonitor.marketdata.application.port.out;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Evento de dividendo canonico a nivel seguridad (ADR-0014 F3b), mapeado del payload de Massive
 * ({@code /stocks/v1/dividends}). {@code cashAmount} = DPS bruto; {@code distributionType} =
 * recurring|special; {@code splitAdjustedCashAmount} = DPS normalizado por splits; {@code
 * historicalAdjustmentFactor} = factor de ajuste de precio del proveedor. {@code providerEventId}
 * es el id del proveedor para dedup fuerte.
 */
public record DividendEventData(
    String ticker,
    LocalDate exDividendDate,
    LocalDate recordDate,
    LocalDate paymentDate,
    LocalDate declarationDate,
    BigDecimal cashAmount,
    String currency,
    Short frequency,
    String distributionType,
    BigDecimal splitAdjustedCashAmount,
    BigDecimal historicalAdjustmentFactor,
    String providerEventId) {}
