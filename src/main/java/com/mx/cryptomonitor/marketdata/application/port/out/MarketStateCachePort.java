package com.mx.cryptomonitor.marketdata.application.port.out;

import java.time.Instant;
import java.util.Optional;

import com.mx.cryptomonitor.marketdata.domain.model.Market;
import com.mx.cryptomonitor.marketdata.domain.model.RemoteMarketState;

/**
 * Recuerda el estado remoto de un mercado para consultarlo una sola vez por periodo de cierre
 * (ADR-0033). De mejor esfuerzo, igual que {@link LastKnownQuotePort}.
 */
public interface MarketStateCachePort {

  Optional<RemoteMarketState> find(Market market);

  void save(Market market, RemoteMarketState state, Instant expiresAt);
}
