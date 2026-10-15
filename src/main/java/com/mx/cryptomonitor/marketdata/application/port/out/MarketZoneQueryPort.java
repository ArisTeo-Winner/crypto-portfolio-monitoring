package com.mx.cryptomonitor.marketdata.application.port.out;

import java.util.Optional;

/**
 * Resuelve la zona horaria IANA de la plaza de un activo a partir de su moneda/exchange (ADR-0013,
 * reusa el mapeo mercado→zona de ADR-0012). API publica de marketdata (named interface {@code
 * port-out}) para que otros modulos fijen el binomio de operacion sin acoplarse al enum interno
 * {@code Market}. Crypto queda fuera (el consumidor aplica UTC): aqui solo se mapean mercados con
 * plaza fisica.
 */
public interface MarketZoneQueryPort {

  /**
   * @return el id IANA de la zona de la plaza (p. ej. {@code America/New_York}), o vacio si la
   *     combinacion moneda/exchange no corresponde a un mercado con plaza conocida.
   */
  Optional<String> zoneIdFor(String currency, String exchange);
}
