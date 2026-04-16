package com.mx.cryptomonitor.marketdata.domain.model;

import java.time.Instant;
import java.util.Objects;

public record PriceQuote(
    String symbol, AssetType assetType, Money price, Instant asOf, ProviderId provider) {

  public PriceQuote {
    Objects.requireNonNull(symbol, "symbol");
    Objects.requireNonNull(assetType, "assetType");
    Objects.requireNonNull(price, "price");
    Objects.requireNonNull(asOf, "asOf");
    Objects.requireNonNull(provider, "provider");
    if (symbol.isBlank()) {
      throw new IllegalArgumentException("symbol must not be blank");
    }
  }
}
