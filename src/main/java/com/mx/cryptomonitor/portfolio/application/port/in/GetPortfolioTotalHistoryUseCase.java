package com.mx.cryptomonitor.portfolio.application.port.in;

import java.util.UUID;

import com.mx.cryptomonitor.portfolio.domain.model.PortfolioHistoryResult;

public interface GetPortfolioTotalHistoryUseCase {

  PortfolioHistoryResult getTotalHistory(UUID userId, String range, String assetTypes);
}
