package com.mx.cryptomonitor.portfolio.domain.model;

import java.util.Locale;

public enum AssetType {
  CRYPTO,
  STOCK,
  ETF,
  INDEX;

  public static AssetType from(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("assetType is required");
    }
    String normalized = value.trim().toUpperCase(Locale.ROOT);
    if ("INDICE".equals(normalized)) {
      return INDEX;
    }
    return AssetType.valueOf(normalized);
  }
}
