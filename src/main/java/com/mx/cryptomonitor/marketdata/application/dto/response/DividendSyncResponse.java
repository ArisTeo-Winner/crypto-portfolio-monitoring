package com.mx.cryptomonitor.marketdata.application.dto.response;

import java.util.Map;

/**
 * Resultado del sync de dividendos on-demand (ADR-0014 F3b). {@code created} mapea cada símbolo al
 * número de eventos NUEVOS insertados; {@code totalCreated} es la suma.
 */
public record DividendSyncResponse(Map<String, Integer> created, int totalCreated) {}
