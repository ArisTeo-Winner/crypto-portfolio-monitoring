package com.mx.cryptomonitor.marketdata.domain.model;

/**
 * Forma de la contraprestacion de una fusion/adquisicion (ADR-0014 F6b). Define como se liquida la
 * tenencia del predecesor: solo efectivo, solo acciones de la sucesora, o hibrido.
 */
public enum MergerType {
  /** Solo efectivo: la posicion se cierra a {@code cash_per_share} (realiza PnL). */
  ALL_CASH,
  /**
   * Solo acciones: la posicion migra a la sucesora por {@code exchange_ratio} (carryover basis).
   */
  ALL_STOCK,
  /** Hibrido: parte efectivo + parte acciones de la sucesora. */
  CASH_AND_STOCK
}
