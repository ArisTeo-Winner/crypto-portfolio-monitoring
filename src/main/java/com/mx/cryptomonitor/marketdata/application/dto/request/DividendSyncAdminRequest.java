package com.mx.cryptomonitor.marketdata.application.dto.request;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

/**
 * Petición admin para poblar dividendos on-demand (ADR-0014 F3b). {@code symbols} son los tickers a
 * sincronizar; usar el sufijo de mercado cuando aplique (p. ej. {@code BIMBOA.MX} para BMV en MXN
 * nativo, {@code GOOG} para US).
 */
public record DividendSyncAdminRequest(@NotEmpty List<@NotBlank String> symbols) {}
