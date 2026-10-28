package com.mx.cryptomonitor.marketdata.domain.model;

/** Fase actual de un mercado (ADR-0012). BMV solo usa {@code OPEN}/{@code CLOSED}. */
public enum MarketPhase {
  OPEN,
  PRE_MARKET,
  AFTER_HOURS,
  CLOSED
}
