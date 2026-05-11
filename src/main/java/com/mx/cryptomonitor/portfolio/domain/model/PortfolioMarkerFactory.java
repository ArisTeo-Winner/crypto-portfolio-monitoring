package com.mx.cryptomonitor.portfolio.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

public final class PortfolioMarkerFactory {

  private static final String BUY = "BUY";
  private static final String SELL = "SELL";
  private static final String BUY_COLOR = "#22c55e";
  private static final String ASSET_BUY_COLOR = "#16a34a";
  private static final String SELL_COLOR = "#ef4444";

  private PortfolioMarkerFactory() {}

  public static PortfolioMarker from(
      Instant time, String transactionType, BigDecimal quantity, String symbol) {
    Objects.requireNonNull(time, "time is required");
    String type = normalize(transactionType, "transactionType");
    String normalizedSymbol = normalize(symbol, "symbol");
    BigDecimal markerQuantity = quantity != null ? quantity : BigDecimal.ZERO;

    if (BUY.equals(type)) {
      return new PortfolioMarker(
          time.getEpochSecond(),
          "belowBar",
          BUY_COLOR,
          "arrowUp",
          BUY + " " + quantityText(markerQuantity) + " " + normalizedSymbol);
    }
    if (SELL.equals(type)) {
      return new PortfolioMarker(
          time.getEpochSecond(),
          "aboveBar",
          SELL_COLOR,
          "arrowDown",
          SELL + " " + quantityText(markerQuantity) + " " + normalizedSymbol);
    }
    throw new IllegalArgumentException("transactionType must be BUY or SELL");
  }

  public static PortfolioMarker assetMarker(
      Instant time, String transactionType, BigDecimal quantity, String symbol, BigDecimal price) {
    Objects.requireNonNull(time, "time is required");
    String type = normalize(transactionType, "transactionType");
    String normalizedSymbol = normalize(symbol, "symbol");
    BigDecimal markerQuantity = quantity != null ? quantity : BigDecimal.ZERO;
    BigDecimal markerPrice = price != null ? price : BigDecimal.ZERO;

    if (BUY.equals(type)) {
      return new PortfolioMarker(
          time.getEpochSecond(),
          "belowBar",
          ASSET_BUY_COLOR,
          "arrowUp",
          BUY
              + " "
              + quantityText(markerQuantity)
              + " "
              + normalizedSymbol
              + " @ "
              + quantityText(markerPrice));
    }
    if (SELL.equals(type)) {
      return new PortfolioMarker(
          time.getEpochSecond(),
          "aboveBar",
          SELL_COLOR,
          "arrowDown",
          SELL
              + " "
              + quantityText(markerQuantity)
              + " "
              + normalizedSymbol
              + " @ "
              + quantityText(markerPrice));
    }
    throw new IllegalArgumentException("transactionType must be BUY or SELL");
  }

  private static String normalize(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " is required");
    }
    return value.trim().toUpperCase(Locale.ROOT);
  }

  private static String quantityText(BigDecimal quantity) {
    return quantity.stripTrailingZeros().toPlainString();
  }
}
