package com.mx.cryptomonitor.marketdata.application.port.out;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Evento de split canonico (ADR-0011). {@code shareMultiplier} = split_to/split_from (1/factor): la
 * cantidad de una operacion anterior a {@code executionDate} se multiplica por el, y el precio
 * unitario se divide por el. Cubre forward_split, reverse_split y stock_dividend por igual.
 */
public record StockSplitData(String ticker, LocalDate executionDate, BigDecimal shareMultiplier) {}
