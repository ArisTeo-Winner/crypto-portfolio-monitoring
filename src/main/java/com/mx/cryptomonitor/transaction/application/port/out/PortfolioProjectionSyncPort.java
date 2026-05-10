package com.mx.cryptomonitor.transaction.application.port.out;

import java.util.Optional;
import java.util.UUID;

public interface PortfolioProjectionSyncPort {

  void reconcileUserPortfolio(UUID userId);

  void recordUserPortfolioSnapshot(UUID userId);

  Optional<UUID> resolvePortfolioEntryId(UUID userId, String assetSymbol);
}
