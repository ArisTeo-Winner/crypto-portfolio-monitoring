package com.mx.cryptomonitor.marketdata.application.port.out;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Evento de dominio que marketdata publica al detectar y persistir un dividendo nuevo (ADR-0014
 * F3c). Lo consume transaction para acreditar por usuario; marketdata no depende de transaction (la
 * relacion es solo este evento, parte de la named interface {@code port-out}).
 *
 * <p>{@code dividendPerShare} es el DPS bruto; la cadena fiscal se aplica aguas abajo. Se publica
 * por cada evento nuevo, incluido el backfill historico (la idempotencia del accrual protege los
 * re-procesos).
 */
public record DividendDetectedEvent(
    String assetSymbol,
    LocalDate exDividendDate,
    LocalDate paymentDate,
    BigDecimal dividendPerShare,
    String currency) {}
