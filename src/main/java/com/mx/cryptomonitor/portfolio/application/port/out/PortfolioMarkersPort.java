package com.mx.cryptomonitor.portfolio.application.port.out;

import java.util.List;
import java.util.UUID;

public interface PortfolioMarkersPort {

  List<PortfolioTransactionSnapshot> getTransactionsByUserAndSymbol(UUID userId, String symbol);
}
