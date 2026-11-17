package com.mx.cryptomonitor.marketdata.domain.model;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Periodo de cierre vigente de un mercado (ADR-0033). {@code sessionDate} es la última sesión
 * regular ya terminada; {@code opensAt} es la campana de la siguiente apertura; {@code settled}
 * indica que ya pasó el margen tras el cierre y el precio de la cadena puede darse por definitivo.
 */
public record ClosedWindow(LocalDate sessionDate, Instant opensAt, boolean settled) {}
