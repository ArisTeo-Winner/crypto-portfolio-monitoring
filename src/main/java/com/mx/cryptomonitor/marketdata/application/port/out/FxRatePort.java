package com.mx.cryptomonitor.marketdata.application.port.out;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Tasa de cambio para normalizar operaciones en distinta moneda a la moneda base del portafolio.
 * Puerto consumido por el modulo portfolio para la valuacion/P&L multi-moneda (ver ADR-0006).
 */
public interface FxRatePort {

  /**
   * Tasa USD/MXN mas reciente conocida (pesos por dolar), o vacio si no hay dato cacheado. v1 usa
   * la tasa actual (no la de la fecha de operacion).
   */
  Optional<BigDecimal> usdMxnRate();

  /**
   * Igual que {@link #usdMxnRate()} pero con procedencia (proveedor + fecha de cotizacion), para el
   * envelope de presentacion (ADR-0010). Vacio si no hay dato disponible.
   */
  Optional<UsdMxnRateDetail> usdMxnRateDetail();
}
