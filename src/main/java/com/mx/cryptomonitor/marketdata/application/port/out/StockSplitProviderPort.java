package com.mx.cryptomonitor.marketdata.application.port.out;

import java.util.List;

/**
 * Proveedor externo del historico de splits de un ticker (ADR-0011). Implementacion: Massive
 * ({@code /stocks/v1/splits?ticker=}). Solo se invoca en la ingesta (fetch-on-miss / sweep), nunca
 * en el hot path de valuacion.
 */
public interface StockSplitProviderPort {

  List<StockSplitData> fetchSplits(String ticker);
}
