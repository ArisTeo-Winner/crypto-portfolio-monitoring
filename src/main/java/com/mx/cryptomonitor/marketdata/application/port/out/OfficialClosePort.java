package com.mx.cryptomonitor.marketdata.application.port.out;

import java.util.Optional;

import com.mx.cryptomonitor.marketdata.domain.model.StockQuote;

/**
 * Cierre de fin de día con la fecha de la sesión a la que pertenece (ADR-0033, regla R4). Devuelve
 * vacío ante cualquier fallo; nunca lanza.
 */
public interface OfficialClosePort {

  Optional<StockQuote> find(String symbol);
}
