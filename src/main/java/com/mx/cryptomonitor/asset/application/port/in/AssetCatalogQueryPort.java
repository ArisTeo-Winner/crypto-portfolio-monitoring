package com.mx.cryptomonitor.asset.application.port.in;

import java.util.Optional;

public interface AssetCatalogQueryPort {

  Optional<String> findAssetIdBySymbol(String symbol);
}
