package com.mx.cryptomonitor.transaction.domain.merger;

import java.math.BigDecimal;

/**
 * Efecto de una fusion sobre la posicion de un usuario en el predecesor (ADR-0014 F6b-2). Se
 * materializa en el ledger reusando TRANSFER + SELL (ADR-0014 F6b-2 decision):
 *
 * <ul>
 *   <li>ALL_STOCK: TRANSFER_OUT del predecesor + TRANSFER_IN del sucesor con {@code
 *       successorQuantity} al costo {@code successorCostBasis} (carryover, sin PnL).
 *   <li>ALL_CASH: SELL del predecesor a {@code cashProceeds} que realiza {@code realizedPnl}.
 * </ul>
 *
 * Invariante: en ALL_STOCK {@code cashProceeds=0, realizedPnl=0}; en ALL_CASH {@code
 * successorQuantity=0, successorCostBasis=0}.
 */
public record MergerEffect(
    BigDecimal successorQuantity,
    BigDecimal successorCostBasis,
    BigDecimal cashProceeds,
    BigDecimal realizedPnl) {}
