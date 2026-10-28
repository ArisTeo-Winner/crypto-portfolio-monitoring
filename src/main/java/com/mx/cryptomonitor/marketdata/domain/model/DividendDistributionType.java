package com.mx.cryptomonitor.marketdata.domain.model;

/**
 * Naturaleza del dividendo (ADR-0014). RECURRING = ordinario/periodico (cuenta para yield TTM y
 * dividend growth); SPECIAL = extraordinario no recurrente (se EXCLUYE del crecimiento y del yield
 * TTM para no distorsionar). Mapea el {@code distribution_type} de Massive.
 */
public enum DividendDistributionType {
  RECURRING,
  SPECIAL
}
