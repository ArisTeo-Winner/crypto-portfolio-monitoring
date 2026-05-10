package com.mx.cryptomonitor.portfolio.application.port.in;

import java.util.List;
import java.util.UUID;

import com.mx.cryptomonitor.portfolio.domain.model.TimeValuePoint;

public interface GetPortfolioTotalHistoryUseCase {

  List<TimeValuePoint> getTotalHistory(UUID userId, String range, String assetTypes);
}
