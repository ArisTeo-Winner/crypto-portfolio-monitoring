package com.mx.cryptomonitor.asset.application.service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.mx.cryptomonitor.asset.application.dto.response.AssetOptionResponse;
import com.mx.cryptomonitor.asset.application.dto.response.AssetSearchResponse;
import com.mx.cryptomonitor.asset.application.port.in.AssetCatalogQueryPort;

@Service
public class AssetSearchService implements AssetCatalogQueryPort {

  private static final int DEFAULT_LIMIT = 10;
  private static final int MAX_LIMIT = 20;
  private static final String CRYPTO = "CRYPTO";

  private static final List<AssetCatalogEntry> CATALOG =
      List.of(
          new AssetCatalogEntry("bitcoin", "BTC", "Bitcoin", CRYPTO, null, true),
          new AssetCatalogEntry("bitcoin-cash", "BCH", "Bitcoin Cash", CRYPTO, null, true),
          new AssetCatalogEntry("ethereum", "ETH", "Ethereum", CRYPTO, null, true),
          new AssetCatalogEntry("tether", "USDT", "Tether USDt", CRYPTO, null, true),
          new AssetCatalogEntry("binancecoin", "BNB", "BNB", CRYPTO, null, true),
          new AssetCatalogEntry("ripple", "XRP", "XRP", CRYPTO, null, true),
          new AssetCatalogEntry("usd-coin", "USDC", "USDC", CRYPTO, null, true));

  public AssetSearchResponse search(String query, Integer limit) {
    String normalizedQuery = normalizeQuery(query);
    int normalizedLimit = normalizeLimit(limit);
    String loweredQuery = normalizedQuery.toLowerCase(Locale.ROOT);

    List<AssetOptionResponse> items =
        CATALOG.stream()
            .filter(entry -> matches(entry, loweredQuery))
            .sorted(searchComparator(loweredQuery))
            .limit(normalizedLimit)
            .map(this::toResponse)
            .toList();

    return new AssetSearchResponse(items, items.size(), normalizedQuery);
  }

  @Override
  public java.util.Optional<String> findAssetIdBySymbol(String symbol) {
    if (symbol == null || symbol.isBlank()) {
      return java.util.Optional.empty();
    }

    String normalizedSymbol = symbol.trim();
    return CATALOG.stream()
        .filter(entry -> entry.symbol().equalsIgnoreCase(normalizedSymbol))
        .map(AssetCatalogEntry::assetId)
        .findFirst();
  }

  private String normalizeQuery(String query) {
    if (query == null || query.trim().isEmpty()) {
      throw new IllegalArgumentException("Query parameter q is required.");
    }
    return query.trim();
  }

  private int normalizeLimit(Integer limit) {
    if (limit == null) {
      return DEFAULT_LIMIT;
    }
    if (limit < 1 || limit > MAX_LIMIT) {
      throw new IllegalArgumentException("Query parameter limit must be between 1 and 20.");
    }
    return limit;
  }

  private boolean matches(AssetCatalogEntry entry, String loweredQuery) {
    return entry.symbol().toLowerCase(Locale.ROOT).contains(loweredQuery)
        || entry.name().toLowerCase(Locale.ROOT).contains(loweredQuery);
  }

  private Comparator<AssetCatalogEntry> searchComparator(String loweredQuery) {
    return Comparator.comparing(
            (AssetCatalogEntry entry) -> !entry.symbol().equalsIgnoreCase(loweredQuery))
        .thenComparing((AssetCatalogEntry entry) -> !entry.name().equalsIgnoreCase(loweredQuery))
        .thenComparing(AssetCatalogEntry::symbol);
  }

  private AssetOptionResponse toResponse(AssetCatalogEntry entry) {
    return new AssetOptionResponse(
        entry.assetId(),
        entry.symbol(),
        entry.name(),
        entry.assetType(),
        entry.logoUrl(),
        entry.supportedForTransactions());
  }

  private record AssetCatalogEntry(
      String assetId,
      String symbol,
      String name,
      String assetType,
      String logoUrl,
      boolean supportedForTransactions) {}
}
