package com.mx.cryptomonitor.portfolio.application.port.in;

import java.util.List;
import java.util.UUID;

import com.mx.cryptomonitor.portfolio.application.dto.response.PortfolioChartPointResponse;
import com.mx.cryptomonitor.portfolio.application.dto.response.PortfolioMarkerResponse;

public interface PortfolioChartPort {

  List<PortfolioChartPointResponse> getEquityHistory(UUID userId, String range);

  List<PortfolioMarkerResponse> getMarkers(UUID userId, String range);

  List<PortfolioChartPointResponse> getRealizedPnl(UUID userId, String range);

  void recordEquitySnapshot(UUID userId);

  void recordEquitySnapshotsForActiveUsers();
}
