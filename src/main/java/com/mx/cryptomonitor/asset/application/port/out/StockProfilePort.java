package com.mx.cryptomonitor.asset.application.port.out;

import java.util.Optional;

/** Puerto hacia el proveedor unificado de perfil de acciones (logo real + market cap). */
public interface StockProfilePort {

  Optional<StockProfile> getProfile(String symbol);

  record StockProfile(
      String symbol,
      String name,
      String logoUrl,
      Long marketCapMillions,
      String exchange,
      String currency) {}
}
