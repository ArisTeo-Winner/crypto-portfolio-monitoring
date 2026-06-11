package com.mx.cryptomonitor.asset.application.port.out;

import java.util.Optional;

public interface AssetProfileProvider {

  Optional<String> getLogoUrl(String symbol);

  Optional<AssetProfile> getProfile(String symbol);

  record AssetProfile(String symbol, String name, String logoUrl) {}
}
