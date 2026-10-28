package com.mx.cryptomonitor.asset.domain.model;

/**
 * Estado de una seguridad en su plaza (ADR-0014 F6a). Se persiste como texto en {@code
 * asset_catalog.market_status} (CHECK en DB); este enum da uso type-safe en el dominio.
 */
public enum MarketStatus {
  /** Cotiza normalmente. Default de las filas legadas. */
  ACTIVE,
  /** Absorbida por una sucesora (ya no cotiza; su historia sigue viva bajo la sucesora). */
  MUTED_BY_MERGER,
  /** Dada de baja del mercado. */
  DELISTED,
  /** En quiebra. */
  BANKRUPT,
  /** Suspendida temporalmente. */
  SUSPENDED
}
