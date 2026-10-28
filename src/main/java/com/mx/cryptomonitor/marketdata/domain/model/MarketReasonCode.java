package com.mx.cryptomonitor.marketdata.domain.model;

/** Motivo de la fase actual (ADR-0012). */
public enum MarketReasonCode {
  REGULAR,
  EARLY_CLOSE,
  PRE_MARKET,
  AFTER_HOURS,
  BEFORE_OPEN,
  AFTER_CLOSE,
  WEEKEND,
  HOLIDAY
}
