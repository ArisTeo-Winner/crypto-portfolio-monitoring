package com.mx.cryptomonitor.marketdata.domain.model;

/**
 * Tipos de corporate action soportados por el framework (ADR-0014). Cada tipo tiene su tabla de
 * detalle 1:1 (split_detail, dividend_detail, merger_detail, ...) o usa el raw_payload de la base
 * para la cola larga.
 */
public enum CorporateActionType {
  SPLIT,
  DIVIDEND,
  MERGER,
  TENDER_OFFER,
  DELISTING,
  BANKRUPTCY,
  SPINOFF
}
