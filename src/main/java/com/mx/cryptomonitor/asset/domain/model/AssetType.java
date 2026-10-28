package com.mx.cryptomonitor.asset.domain.model;

import java.util.Locale;

public enum AssetType {
  CRYPTO,
  STOCK,
  ETF,
  GOVERNMENT_BOND,
  CORPORATE_BOND,
  INDEX,
  FOREX;

  public static AssetType fromString(String value) {
    try {
      return AssetType.valueOf(value.trim().toUpperCase(Locale.ROOT));
    } catch (IllegalArgumentException | NullPointerException ex) {
      throw new IllegalArgumentException("Query parameter type must be a valid asset type.");
    }
  }
}
