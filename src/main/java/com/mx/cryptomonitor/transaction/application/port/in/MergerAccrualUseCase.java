package com.mx.cryptomonitor.transaction.application.port.in;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Acredita una fusión a todos los usuarios con posición en el predecesor (ADR-0014 F6b-2). Devuelve
 * cuántos usuarios fueron acreditados.
 */
public interface MergerAccrualUseCase {

  int accrue(
      String predecessorSymbol,
      String successorSymbol,
      LocalDate effectiveDate,
      BigDecimal exchangeRatio,
      BigDecimal cashPerShare,
      String currency);
}
