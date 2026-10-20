package com.mx.cryptomonitor.transaction.application.port.in;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Acredita un dividendo detectado a todos los usuarios con derecho (ADR-0014 F3c). Trigger inbound:
 * el listener del evento {@code DividendDetectedEvent}. Devuelve cuantas transacciones DIVIDEND se
 * crearon.
 */
public interface DividendAccrualUseCase {

  int accrue(
      String assetSymbol,
      String assetType,
      LocalDate exDividendDate,
      LocalDate paymentDate,
      BigDecimal dividendPerShare,
      String currency,
      String exchange);
}
