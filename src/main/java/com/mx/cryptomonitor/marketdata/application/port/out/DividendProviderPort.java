package com.mx.cryptomonitor.marketdata.application.port.out;

import java.util.List;

/**
 * Proveedor externo del historico de dividendos de un ticker (ADR-0014 F3b). Implementacion:
 * Massive ({@code /stocks/v1/dividends?ticker=}). Solo se invoca en la ingesta (backfill / sweep),
 * nunca en el hot path. Devuelve lista vacia si no esta configurado o falla (sin romper).
 */
public interface DividendProviderPort {

  List<DividendEventData> fetchDividends(String ticker);
}
