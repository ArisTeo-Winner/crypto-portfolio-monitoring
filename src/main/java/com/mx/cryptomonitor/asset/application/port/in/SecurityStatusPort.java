package com.mx.cryptomonitor.asset.application.port.in;

/**
 * Transiciones de estado de una seguridad en el catálogo (ADR-0014 F6c+). API pública de asset
 * (named interface port.in) para que otros módulos marquen bajas sin acoplarse al enum interno
 * {@code MarketStatus} (métodos semánticos, no un set genérico). Best-effort: solo toca la fila si
 * existe.
 */
public interface SecurityStatusPort {

  /** Marca la seguridad como dada de baja del mercado (DELISTED). */
  void markDelisted(String symbol);

  /** Marca la seguridad como en quiebra (BANKRUPT). */
  void markBankrupt(String symbol);
}
