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

  /**
   * Resuelve nombre + logo de display del catálogo para un conjunto de símbolos, deduplicando
   * (ADR-0008). El catálogo es la única fuente de verdad de estos metadatos: el hot path de
   * listados (p.ej. /me/transactions) lee de aquí en lugar de un valor denormalizado y congelado.
   * Una sola resolución por símbolo distinto, desde el catálogo cacheado (Redis), sin proveedores
   * externos.
   *
   * @return mapa {@code SYMBOL(upper) -> AssetDisplay}; solo incluye símbolos presentes en el
   *     catálogo. {@code name} puede ser {@code null} si aún no se resolvió; {@code logoUrl} puede
   *     ser {@code null} si no hay logo.
   */
  Map<String, AssetDisplay> findDisplayBySymbols(Collection<String> symbols);

  /** Metadatos de display de un activo resueltos desde el catálogo. */
  record AssetDisplay(String name, String logoUrl) {}
}
