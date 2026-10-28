package com.mx.cryptomonitor.asset.application.port.out;

import java.util.List;

import com.mx.cryptomonitor.asset.application.dto.AssetCatalogDto;

public interface CatalogFetchPort {

  List<AssetCatalogDto> fetchTopStocks(int limit);

  List<AssetCatalogDto> fetchTopEtfs(int limit);
}
