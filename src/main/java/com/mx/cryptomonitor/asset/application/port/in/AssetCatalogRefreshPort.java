package com.mx.cryptomonitor.asset.application.port.in;

import java.util.Map;

/**
 * Refresco on-demand del catálogo de activos (patrón <em>pull-once</em>): la primera vez que se ve
 * un símbolo aún no catalogado, se resuelve su ícono <b>una sola vez</b> desde los proveedores y se
 * persiste en el catálogo (DB + Redis). En resoluciones posteriores el catálogo ya lo tiene; si no
 * hay logo posible se marca {@code logo_status=NONE} (cache de negativos) para no reintentar.
 */
public interface AssetCatalogRefreshPort {

  /**
   * Versión asíncrona best-effort (fire-and-forget), para no bloquear el flujo que la invoca (p.ej.
   * la creación de una transacción). No-op si ya está resuelto o marcado NONE.
   */
  void ensureIconCatalogued(String symbol, String assetType);

  /**
   * Versión síncrona: resuelve/actualiza+cachea íconos y devuelve {@code SYMBOL(upper) -> url} de
   * los que quedaron con logo. Deduplica y solo golpea a los proveedores cuando hace falta:
   * símbolos aún no resueltos (una sola vez por activo) e íconos <b>provisionales</b> (fallback
   * determinista) cuyo throttle venció, que se auto-actualizan al logo autoritativo (p.ej. jsDelivr
   * → CoinGecko). Los ya resueltos con logo autoritativo, los marcados NONE y los provisionales
   * dentro de su ventana de reintento no generan llamada. Pensado para el hot path de lectura, de
   * modo que un ícono faltante o corregido aparezca en el <b>mismo</b> request.
   *
   * @param symbolToType mapa {@code símbolo -> tipo} (STOCK, CRYPTO, …)
   */
  Map<String, String> resolveMissingIcons(Map<String, String> symbolToType);
}
