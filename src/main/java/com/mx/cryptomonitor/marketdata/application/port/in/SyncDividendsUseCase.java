package com.mx.cryptomonitor.marketdata.application.port.in;

/**
 * Dispara la ingesta de dividendos de una seguridad (ADR-0014 F3b). Lo invoca el endpoint admin
 * on-demand ({@code POST /api/v1/admin/corporate-actions/dividends/sync}) y, a futuro, el barrido
 * programado. Devuelve cuántos eventos NUEVOS se insertaron (0 si ya estaban o no hay datos).
 */
public interface SyncDividendsUseCase {

  /**
   * Sincroniza los dividendos de una seguridad. Devuelve cuántos eventos NUEVOS se insertaron (0 si
   * ya estaban o el proveedor no tiene datos).
   */
  int sync(String symbol);
}
