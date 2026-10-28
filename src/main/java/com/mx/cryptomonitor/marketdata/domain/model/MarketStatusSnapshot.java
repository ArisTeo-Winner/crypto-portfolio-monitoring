package com.mx.cryptomonitor.marketdata.domain.model;

import java.time.Instant;

/**
 * Estado computado de un mercado en un instante (ADR-0012). {@code nextChangeAt} es el instante UTC
 * del próximo cambio relevante (apertura o cierre de la sesión regular); {@code nextChangeType}
 * dice si en ese instante el mercado abre o cierra.
 */
public record MarketStatusSnapshot(
    Market market,
    MarketPhase phase,
    MarketReasonCode reasonCode,
    boolean isOpen,
    NextChangeType nextChangeType,
    Instant nextChangeAt) {

  public enum NextChangeType {
    OPEN,
    CLOSE
  }
}
