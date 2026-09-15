package com.mx.cryptomonitor.asset.application.port.in;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public interface AssetCatalogQueryPort {

  Optional<String> findAssetIdBySymbol(String symbol);

  Optional<String> findNameBySymbol(String symbol);

  /**
   * Resuelve las URLs de logo del catálogo para un conjunto de símbolos, deduplicando. Pensado para
   * el hot path de listados (p.ej. /me/transactions): una sola resolución por símbolo distinto,
   * leyendo del catálogo cacheado (Redis) — sin llamar a proveedores externos.
   *
   * @return mapa {@code SYMBOL(upper) -> logoUrl}; solo incluye símbolos con logo no vacío
   */
  Map<String, String> findLogosBySymbols(Collection<String> symbols);
}
