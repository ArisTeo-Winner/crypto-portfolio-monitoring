package com.mx.cryptomonitor.marketdata.application.port.in;

/**
 * Re-emite los dividendos YA detectados de un símbolo para disparar el accrual por usuario
 * (ADR-0027). Resuelve el "late joiner": un usuario que registra un holding cuyos dividendos ya
 * están en el catálogo no recibiría su acreditación porque el evento solo se publica al INSERTAR un
 * dividendo nuevo. Re-emitir es seguro: el accrual es idempotente por {@code (usuario, símbolo,
 * ex-date)}, así que solo acredita al nuevo tenedor y salta a los ya acreditados.
 */
public interface ReplayDividendAccrualUseCase {

  /**
   * Publica un {@code DividendDetectedEvent} por cada dividendo ya persistido del símbolo. Devuelve
   * cuántos se re-emitieron.
   */
  int replayForAccrual(String symbol);
}
