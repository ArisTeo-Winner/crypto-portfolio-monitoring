package com.mx.cryptomonitor.marketdata.application.port.out;

import java.util.Optional;

import com.mx.cryptomonitor.marketdata.domain.model.Market;
import com.mx.cryptomonitor.marketdata.domain.model.RemoteMarketState;

/**
 * Segunda opinión externa sobre si un mercado está abierto y cuándo abre (ADR-0033). Devuelve vacío
 * ante cualquier fallo o respuesta ilegible; nunca lanza.
 */
public interface RemoteMarketStatePort {

  Optional<RemoteMarketState> fetch(Market market);
}
