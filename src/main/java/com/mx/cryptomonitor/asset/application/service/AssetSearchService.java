package com.mx.cryptomonitor.asset.application.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.mx.cryptomonitor.asset.application.dto.AssetCatalogDto;
import com.mx.cryptomonitor.asset.application.dto.response.AssetOptionResponse;
import com.mx.cryptomonitor.asset.application.dto.response.AssetSearchResponse;
import com.mx.cryptomonitor.asset.application.port.in.AssetCatalogQueryPort;
import com.mx.cryptomonitor.asset.application.port.out.CatalogFetchPort;
import com.mx.cryptomonitor.asset.application.port.out.CatalogStorePort;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AssetSearchService implements AssetCatalogQueryPort {

  private static final int DEFAULT_LIMIT = 10;
  private static final int MAX_LIMIT = 20;
  private static final Duration MISC_TTL = Duration.ofHours(24);

  private final CatalogStorePort redisService;
  private final CatalogFetchPort fmpAdapter;

  public AssetSearchResponse search(String query, Integer limit) {
    String q = normalizeQuery(query);
    int lim = normalizeLimit(limit);
    String lower = q.toLowerCase(Locale.ROOT);

    List<AssetCatalogDto> candidates = searchInRedis(lower);

    if (candidates.isEmpty()) {
      candidates = lookupOnDemand(q.toUpperCase());
    }

    List<AssetOptionResponse> items =
        candidates.stream()
            .sorted(searchComparator(lower))
            .limit(lim)
            .map(this::toResponse)
            .toList();

    return new AssetSearchResponse(items, items.size(), q);
  }

  @Override
  public Optional<String> findAssetIdBySymbol(String symbol) {
    return redisService.findEntry(symbol).map(AssetCatalogDto::symbol);
  }

  @Override
  public Optional<String> findNameBySymbol(String symbol) {
    return redisService.findEntry(symbol).map(AssetCatalogDto::name);
  }

  public List<AssetOptionResponse> getPopular(String assetType) {
    String key = "catalog:top10:" + assetType.toLowerCase(Locale.ROOT);
    return redisService.getTopSymbols(key, 10).stream()
        .map(symbol -> redisService.findEntry(symbol))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .map(this::toResponse)
        .toList();
  }

  private List<AssetCatalogDto> searchInRedis(String lower) {
    List<String> rankingKeys =
        List.of("catalog:search:stock", "catalog:search:etf", "catalog:search:crypto");
    List<AssetCatalogDto> results = new ArrayList<>();
    for (String key : rankingKeys) {
      redisService.getTopSymbols(key, 50).stream()
          .map(symbol -> redisService.findEntry(symbol))
          .filter(Optional::isPresent)
          .map(Optional::get)
          .filter(dto -> matches(dto, lower))
          .forEach(results::add);
    }
    return results;
  }

  private List<AssetCatalogDto> lookupOnDemand(String symbol) {
    List<AssetCatalogDto> found =
        fmpAdapter.fetchTopStocks(1).stream()
            .filter(d -> d.symbol().equalsIgnoreCase(symbol))
            .toList();
    if (!found.isEmpty()) {
      redisService.saveMiscEntry(found.get(0), MISC_TTL);
    }
    return found;
  }

  private boolean matches(AssetCatalogDto dto, String lower) {
    return dto.symbol().toLowerCase(Locale.ROOT).contains(lower)
        || dto.name().toLowerCase(Locale.ROOT).contains(lower);
  }

  private Comparator<AssetCatalogDto> searchComparator(String lower) {
    return Comparator.comparing((AssetCatalogDto d) -> !d.symbol().equalsIgnoreCase(lower))
        .thenComparing((AssetCatalogDto d) -> !d.name().equalsIgnoreCase(lower))
        .thenComparing(AssetCatalogDto::symbol);
  }

  private AssetOptionResponse toResponse(AssetCatalogDto dto) {
    return new AssetOptionResponse(
        dto.symbol().toLowerCase(Locale.ROOT),
        dto.symbol(),
        dto.name(),
        dto.assetType(),
        dto.logoUrl(),
        !"INDEX".equals(dto.assetType()));
  }

  private String normalizeQuery(String query) {
    if (query == null || query.trim().isEmpty())
      throw new IllegalArgumentException("Query parameter q is required.");
    return query.trim();
  }

  private int normalizeLimit(Integer limit) {
    if (limit == null) return DEFAULT_LIMIT;
    if (limit < 1 || limit > MAX_LIMIT)
      throw new IllegalArgumentException("Query parameter limit must be between 1 and 20.");
    return limit;
  }
}
