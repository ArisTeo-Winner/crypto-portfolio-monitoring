package com.mx.cryptomonitor.portfolio.domain.model;

import java.util.Locale;

/**
 * Moneda de presentacion soportada (ADR-0010). La base funcional es siempre USD; esto solo afecta
 * el formato de salida. v1 soporta USD y MXN; cualquier codigo no soportado (p.ej. EUR) cae a USD.
 */
public enum PresentationCurrency {
  USD,
  MXN;

  public static PresentationCurrency fromCodeOrDefault(String code) {
    if (code == null || code.isBlank()) {
      return USD;
    }
    try {
      return PresentationCurrency.valueOf(code.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException ex) {
      return USD;
    }
  }
}
