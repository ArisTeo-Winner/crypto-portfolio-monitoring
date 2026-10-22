package com.mx.cryptomonitor.transaction.application.port.in;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Acredita una quiebra a todos los usuarios con posición en la seguridad (ADR-0014 F6d-2): cierra
 * la posición a {@code recoveryPerShare} (SELL que realiza la pérdida; 0 = write-off total) y marca
 * la seguridad BANKRUPT. Devuelve cuántos usuarios fueron acreditados.
 */
public interface BankruptcyAccrualUseCase {

  int accrue(String symbol, BigDecimal recoveryPerShare, String currency, LocalDate effectiveDate);
}
