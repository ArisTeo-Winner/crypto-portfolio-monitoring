package com.mx.cryptomonitor.tax.domain.model;

/**
 * Jurisdicción del mercado donde se opera el holding, usada para rutear el régimen fiscal (ADR-0017
 * F1). {@code MX} = bolsa mexicana (BMV/SIC, pesos), {@code US} = mercado estadounidense, {@code
 * OTHER} = sin resolver en esta fase.
 */
public enum MarketJurisdiction {
  MX,
  US,
  OTHER
}
