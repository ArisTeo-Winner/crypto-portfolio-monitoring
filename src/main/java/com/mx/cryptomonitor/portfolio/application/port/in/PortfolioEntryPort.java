package com.mx.cryptomonitor.portfolio.application.port.in;

import java.util.UUID;

public interface PortfolioEntryPort {

  UUID applyTransaction(UUID userId, PortfolioTransactionCommand command);

  void reconcilePortfolio(UUID userId);
}
