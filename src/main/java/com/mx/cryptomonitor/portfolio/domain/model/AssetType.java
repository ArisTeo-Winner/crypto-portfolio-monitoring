package com.mx.cryptomonitor.portfolio.domain.model;

import java.util.Locale;
import java.util.Optional;

public enum AssetType {
  CRYPTO("Crypto", "Criptomonedas y tokens digitales", "crypto"),
  STOCK("Stocks", "Acciones individuales de empresas", "stock"),
  ETF("ETFs", "Fondos cotizados que agrupan varios activos", "etf"),
  GOVERNMENT_BOND(
      "Gov. Bonds", "Bonos gubernamentales (CETES, Treasuries, Bondes)", "government_bond"),
  CORPORATE_BOND("Corp. Bonds", "Bonos corporativos", "corporate_bond"),
  INDEX("Indices", "Conjuntos de acciones que reflejan el rendimiento de un mercado", "index"),
  FOREX("Forex", "Pares de divisas del mercado cambiario", "forex");

  private final String label;
  private final String description;
  private final String icon;

  AssetType(String label, String description, String icon) {
    this.label = label;
    this.description = description;
    this.icon = icon;
  }

  public String label() {
    return label;
  }

  public String description() {
    return description;
  }

  public String icon() {
    return icon;
  }

  /**
   * Convierte un string al enum, manejando alias históricos (INDICE → INDEX, BOND/BONDS/BONOS →
   * GOVERNMENT_BOND). Lanza excepción para valores nulos o en blanco; usa {@link #fromSafe} si el
   * valor puede ser desconocido.
   */
  public static AssetType from(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("assetType is required");
    }
    String normalized = value.trim().toUpperCase(Locale.ROOT);
    if ("INDICE".equals(normalized)) {
      return INDEX;
    }
    if ("BONOS".equals(normalized) || "BOND".equals(normalized) || "BONDS".equals(normalized)) {
      return GOVERNMENT_BOND;
    }
    return AssetType.valueOf(normalized);
  }

  /**
   * Igual que {@link #from} pero devuelve {@link Optional#empty()} en lugar de lanzar excepción
   * cuando el valor no corresponde a ningún tipo conocido. Permite que el portafolio histórico
   * ignore tipos sin lógica de precios, sin romper el flujo.
   */
  public static Optional<AssetType> fromSafe(String value) {
    if (value == null || value.isBlank()) {
      return Optional.empty();
    }
    try {
      return Optional.of(from(value));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }
}
