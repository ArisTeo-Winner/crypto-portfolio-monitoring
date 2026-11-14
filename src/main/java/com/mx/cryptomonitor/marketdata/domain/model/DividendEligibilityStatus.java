package com.mx.cryptomonitor.marketdata.domain.model;

/**
 * Estado de elegibilidad de dividendos de un activo (ADR-0025). Es el resultado PERSISTIDO del
 * descubrimiento empírico (ADR-0024): no hay lista mantenida a mano ni marca del usuario, la verdad
 * la da el proveedor y aquí se recuerda con semántica explícita.
 *
 * <ul>
 *   <li>{@code UNKNOWN} — aún no verificado (no debería persistirse; es el implícito de un símbolo
 *       sin fila).
 *   <li>{@code PAYS} — el proveedor devolvió pagos → el activo paga dividendos.
 *   <li>{@code NONE} — el proveedor respondió OK y vacío → confirmado que no paga.
 *   <li>{@code ERROR} — el proveedor falló/sin cuota → NO concluyente, re-intentar (Fase 2; no se
 *       degrada a {@code NONE}).
 * </ul>
 */
public enum DividendEligibilityStatus {
  UNKNOWN,
  PAYS,
  NONE,
  ERROR
}
