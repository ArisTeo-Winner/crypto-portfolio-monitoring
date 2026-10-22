package com.mx.cryptomonitor.transaction.application.port.in;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Acredita una oferta pública (OPA) a todos los usuarios con posición en la seguridad objeto
 * (ADR-0014 F6c-2): cierra la posición a {@code offerPrice} (SELL, realiza PnL). Asume aceptación
 * total. Devuelve cuántos usuarios fueron acreditados.
 */
public interface TenderAccrualUseCase {

  int accrue(
      String targetSymbol,
      BigDecimal offerPrice,
      String currency,
      LocalDate offerDate,
      boolean resultedInDelisting);
}
