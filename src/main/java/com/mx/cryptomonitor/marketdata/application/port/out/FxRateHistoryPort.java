package com.mx.cryptomonitor.marketdata.application.port.out;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Tipo de cambio USD/MXN aplicable a una fecha de operacion (trade-date FX, ADR-0009). Devuelve el
 * FIX de esa fecha o, si no hubo publicacion (fin de semana/feriado), el FIX vigente mas reciente
 * anterior. Vacio solo si no hay ningun dato disponible ni fallback.
 */
public interface FxRateHistoryPort {

  Optional<BigDecimal> usdMxnRateOn(LocalDate date);
}
