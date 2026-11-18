package com.mx.cryptomonitor.marketdata.domain.model;

/** Cómo se confirmó el precio retenido durante el cierre del mercado (ADR-0033, regla R4). */
public enum HeldQuoteSource {
  /** Cierre con fecha de sesión confirmada por el proveedor. */
  OFFICIAL_EOD,
  /** Precio de la cadena, dado por definitivo al pasar el margen tras el cierre. */
  SETTLED_CHAIN
}
