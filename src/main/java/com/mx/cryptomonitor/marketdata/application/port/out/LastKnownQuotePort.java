package com.mx.cryptomonitor.marketdata.application.port.out;

import java.time.Instant;
import java.util.Optional;

import com.mx.cryptomonitor.marketdata.domain.model.HeldQuoteSource;
import com.mx.cryptomonitor.marketdata.domain.model.StockQuote;

/**
 * Almacén de cotizaciones de acciones fuera de la caché de 15 minutos (ADR-0033). Es de mejor
 * esfuerzo: una falla del almacén se comporta como "no hay dato" y nunca interrumpe la consulta.
 */
public interface LastKnownQuotePort {

  /** Cierre retenido; mientras exista no se consulta a ningún proveedor. */
  Optional<StockQuote> findHeld(String symbol);

  /** Último precio que un proveedor entregó con éxito, para servir ante un fallo total. */
  Optional<StockQuote> findLast(String symbol);

  void saveLast(String symbol, StockQuote quote);

  /** Retiene {@code quote} hasta la campana de apertura {@code opensAt}. */
  void hold(String symbol, StockQuote quote, HeldQuoteSource source, Instant opensAt);
}
