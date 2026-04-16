package com.mx.cryptomonitor.shared.errors;

/**
 * Categorías de error "de negocio" que tu sistema expone hacia arriba (dominio, API REST, front,
 * etc.), independientemente del proveedor.
 */
public enum MarketDataErrorCategory {
  VALIDATION, // 400 Error en los parámetros de entrada (símbolo inválido, etc.)
  NOT_FOUND, // 404 No hay datos de mercado para el activo
  RATE_LIMIT, // 429 Se alcanzó el límite de peticiones del proveedor
  PROVIDER_UNAVAILABLE, // 503 El proveedor está caído o responde 5xx
  PROVIDER_UNAUTHORIZED, // 401 API key mala, permisos insuficientes (401/403)
  INTERNAL // 500 Cualquier error interno no clasificado
}
