package com.mx.cryptomonitor.tax.domain.model;

import java.math.BigDecimal;

/**
 * Una venta ya clasificada por régimen, lista para agregar fiscalmente (ADR-0017 F1). {@code gain}
 * es el resultado realizado (positivo = ganancia, negativo = pérdida) en la moneda fiscal (MXN);
 * para bolsa MXN-nativa es directo, la re-expresión FX fiscal de activos US llega en fases
 * posteriores.
 */
public record TaxableSale(TaxRegime regime, String assetSymbol, int fiscalYear, BigDecimal gain) {}
