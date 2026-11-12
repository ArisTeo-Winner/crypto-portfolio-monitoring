package com.mx.cryptomonitor.transaction.domain.model;

/**
 * Origen de un dividendo registrado (SPEC reconciliación al borrar). {@code ACCRUED} =
 * auto-generado por el accrual a partir del entitlement (derivado del historial de tenencia; se
 * reconcilia al mutar la tenencia). {@code MANUAL} = capturado por el usuario (autoritativo; la
 * reconciliación NUNCA lo toca).
 */
public enum DividendOrigin {
  ACCRUED,
  MANUAL
}
