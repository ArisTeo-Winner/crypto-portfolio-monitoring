package com.mx.cryptomonitor.marketdata.application.port.out;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Evento de dominio que marketdata publica al registrar una fusión nueva (ADR-0015). Lo consume
 * transaction para el accrual del carryover (F6b-2); marketdata no depende de transaction (la
 * relación es solo este evento, parte de la named interface {@code port-out}).
 *
 * <p>{@code successorSymbol} nulo en ALL_CASH; {@code exchangeRatio} nulo en ALL_CASH; {@code
 * cashPerShare} nulo en ALL_STOCK. El accrual infiere el caso por estos términos.
 */
public record MergerDetectedEvent(
    String predecessorSymbol,
    String successorSymbol,
    LocalDate effectiveDate,
    BigDecimal exchangeRatio,
    BigDecimal cashPerShare,
    String currency) {}
