package com.mx.cryptomonitor.transaction.application.port.in;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Acredita un spinoff a todos los usuarios con posición en la matriz (ADR-0014 F6e-2): re-basa la
 * matriz (misma cantidad, base reducida) y les entrega acciones de la spinco con la base asignada.
 * Devuelve cuántos usuarios fueron acreditados.
 */
public interface SpinoffAccrualUseCase {

  int accrue(
      String parentSymbol,
      String spincoSymbol,
      BigDecimal sharesPerParentShare,
      BigDecimal costBasisAllocationPct,
      String currency,
      LocalDate effectiveDate);
}
