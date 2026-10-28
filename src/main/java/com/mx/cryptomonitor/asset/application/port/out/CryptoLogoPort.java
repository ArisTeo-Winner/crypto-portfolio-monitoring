package com.mx.cryptomonitor.asset.application.port.out;

import java.util.Collection;
import java.util.Map;

/**
 * Resuelve la URL del logo de activos CRYPTO desde un proveedor externo (CoinGecko).
 *
 * <p>Contrato batch: se pasa el conjunto de símbolos del catálogo y se devuelve un mapa {@code
 * SYMBOL(upper) -> imageUrl}. Solo debe invocarse en el sync de catálogo (background), nunca en el
 * hot path de peticiones de usuario.
 */
public interface CryptoLogoPort {

  /**
   * @param symbols símbolos de cripto (p.ej. BTC, ETH); mayúsculas/minúsculas indistintas
   * @return mapa {@code SYMBOL(upper) -> imageUrl}; vacío si el proveedor falla o no hay datos (el
   *     llamador aplica su propio fallback)
   */
  Map<String, String> fetchLogosBySymbol(Collection<String> symbols);
}
