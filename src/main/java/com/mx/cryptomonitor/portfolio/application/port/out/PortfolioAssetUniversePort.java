package com.mx.cryptomonitor.portfolio.application.port.out;

import java.util.List;
import java.util.UUID;

import com.mx.cryptomonitor.portfolio.domain.model.AssetType;

public interface PortfolioAssetUniversePort {

  List<PortfolioAssetReference> getAssetsByUser(UUID userId);

  record PortfolioAssetReference(AssetType assetType, String symbol) {}
}
