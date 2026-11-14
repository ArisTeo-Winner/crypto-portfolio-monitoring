package com.mx.cryptomonitor.portfolio.application.port.in;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Ganancias/pérdidas realizadas (ventas) de un usuario, calculadas con el método de costeo
 * solicitado. API pública de portfolio (named interface port.in) para que el módulo {@code tax}
 * agregue fiscalmente reutilizando los engines de portfolio (FIFO/ACB) sin acoplarse al modelo de
 * dominio ni a los motores internos (ADR-0017 F1). Devuelve DTOs planos, no entidades ni puntos de
 * dominio.
 */
public interface RealizedGainsQueryPort {

  List<RealizedGain> realizedGains(UUID userId, CostingMethod method);

  /** Método de costeo soportado por los engines de portfolio. */
  enum CostingMethod {
    AVERAGE_COST,
    FIFO
  }

  /**
   * Una venta realizada en su moneda nativa de operación. {@code realizedAmount} es el P&L
   * realizado neto de comisión (producto − costo − fee), con el signo tal cual lo devuelve el
   * engine.
   */
  record RealizedGain(
      String assetSymbol,
      String assetType,
      String currency,
      LocalDate saleDate,
      BigDecimal realizedAmount) {}
}
