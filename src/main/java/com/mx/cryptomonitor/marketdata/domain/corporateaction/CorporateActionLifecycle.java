package com.mx.cryptomonitor.marketdata.domain.corporateaction;

import java.time.LocalDate;

import com.mx.cryptomonitor.marketdata.domain.model.CorporateActionStatus;

/**
 * Lifecycle de un corporate action (ADR-0020): el estado se DERIVA de las fechas, no se congela al
 * sync-time (asi una fila vieja no queda con estado obsoleto). Con payment_date ya pasado el evento
 * esta {@code COMPLETED} = pagado = inmutable (freeze-al-pago); con ex-date pasada pero sin pago,
 * {@code EFFECTIVE}; si ninguna, {@code ANNOUNCED}. {@code COMPLETED} es terminal: sus montos y
 * fechas no se re-escriben jamas.
 */
public final class CorporateActionLifecycle {

  private CorporateActionLifecycle() {}

  public static CorporateActionStatus statusFor(
      LocalDate exDate, LocalDate paymentDate, LocalDate today) {
    if (paymentDate != null && !paymentDate.isAfter(today)) {
      return CorporateActionStatus.COMPLETED;
    }
    if (exDate != null && !exDate.isAfter(today)) {
      return CorporateActionStatus.EFFECTIVE;
    }
    return CorporateActionStatus.ANNOUNCED;
  }

  /** Un evento congelado (pagado) es inmutable: sus montos y fechas no se re-escriben. */
  public static boolean isFrozen(CorporateActionStatus status) {
    return status == CorporateActionStatus.COMPLETED;
  }
}
