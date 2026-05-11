package com.mx.cryptomonitor.portfolio.application.port.in;

import java.util.List;
import java.util.UUID;

import com.mx.cryptomonitor.portfolio.domain.model.PortfolioMarker;

public interface GetAssetMarkersUseCase {

  List<PortfolioMarker> getAssetMarkers(UUID userId, String symbol, String range);
}
