package com.mx.cryptomonitor.marketdata.application.port.out;

import java.util.Optional;
import java.util.UUID;

/** Puerto hacia el modulo de transacciones para leer una posicion de bono gubernamental. */
public interface GovBondPositionLookupPort {

  Optional<GovBondPositionView> findByTransactionIdAndUserId(UUID transactionId, UUID userId);
}
