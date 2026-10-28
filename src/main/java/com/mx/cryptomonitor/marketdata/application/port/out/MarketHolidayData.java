package com.mx.cryptomonitor.marketdata.application.port.out;

import java.time.LocalDate;
import java.time.LocalTime;

import com.mx.cryptomonitor.marketdata.domain.model.Market;

/**
 * Festivo/cierre bursátil traído de un proveedor externo (ADR-0012, F4). {@code earlyClose} null =
 * cierre total; no-null = medio día (hora local del mercado).
 */
public record MarketHolidayData(Market market, LocalDate date, String name, LocalTime earlyClose) {}
