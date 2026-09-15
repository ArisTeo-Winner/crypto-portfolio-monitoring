package com.mx.cryptomonitor.asset.application.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import com.mx.cryptomonitor.asset.application.dto.AssetCatalogDto;
import com.mx.cryptomonitor.asset.application.dto.response.AssetOptionResponse;
import com.mx.cryptomonitor.asset.application.dto.response.AssetSearchResponse;
import com.mx.cryptomonitor.asset.application.port.in.AssetCatalogQueryPort;
import com.mx.cryptomonitor.asset.application.port.out.CatalogFetchPort;
import com.mx.cryptomonitor.asset.application.port.out.CatalogStorePort;
import com.mx.cryptomonitor.asset.domain.exception.CatalogFetchException;
import com.mx.cryptomonitor.asset.domain.model.AssetCatalogEntity;
import com.mx.cryptomonitor.asset.domain.model.AssetType;
import com.mx.cryptomonitor.asset.domain.repository.AssetCatalogRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssetSearchService implements AssetCatalogQueryPort {

  private static final int DEFAULT_LIMIT = 10;
  private static final int MAX_LIMIT = 20;
  private static final Duration MISC_TTL = Duration.ofHours(24);

  private final CatalogStorePort redisService;
  private final CatalogFetchPort fmpAdapter;
  private final AssetCatalogRepository assetCatalogRepository;

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

  @Override
  public Map<String, String> findLogosBySymbols(Collection<String> symbols) {
    if (symbols == null || symbols.isEmpty()) {
      return Map.of();
    }
    Map<String, String> logos = new HashMap<>();
    for (String symbol : symbols) {
      if (symbol == null || symbol.isBlank()) {
        continue;
      }
      String upper = symbol.trim().toUpperCase(Locale.ROOT);
      if (logos.containsKey(upper)) {
        continue;
      }
      redisService
          .findEntry(upper)
          .map(AssetCatalogDto::logoUrl)
          .filter(url -> url != null && !url.isBlank())
          .ifPresent(url -> logos.put(upper, url));
    }
    return logos;
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

  public List<AssetOptionResponse> listByType(String type, int limit) {
    if (type == null) {
      return assetCatalogRepository.findAll(PageRequest.of(0, limit, Sort.by("symbol"))).stream()
          .map(this::toDto)
          .map(this::toResponse)
          .toList();
    }

    String normalizedType = AssetType.fromString(type).name();
    String rankingKey = "catalog:search:" + normalizedType.toLowerCase(Locale.ROOT);

    List<AssetCatalogDto> entries =
        redisService.getTopSymbols(rankingKey, limit).stream()
            .map(redisService::findEntry)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .toList();

    if (entries.isEmpty()) {
      entries =
          assetCatalogRepository.findByAssetType(normalizedType).stream()
              .limit(limit)
              .map(this::toDto)
              .toList();
    }

    return entries.stream().map(this::toResponse).toList();
  }

  private AssetCatalogDto toDto(AssetCatalogEntity entity) {
    return new AssetCatalogDto(
        entity.getSymbol(),
        entity.getName(),
        entity.getAssetType(),
        entity.getLogoUrl(),
        entity.getExchange(),
        entity.getCurrency(),
        entity.getMarketCap());
  }

  private List<AssetCatalogDto> searchInRedis(String lower) {
    List<String> rankingKeys =
        List.of(
            "catalog:search:stock",
            "catalog:search:etf",
            "catalog:search:crypto",
            "catalog:search:government_bond");
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
    List<AssetCatalogDto> found;
    try {
      found =
          fmpAdapter.fetchTopStocks(1).stream()
              .filter(d -> d.symbol().equalsIgnoreCase(symbol))
              .toList();
    } catch (CatalogFetchException e) {
      log.warn(
          "Lookup on-demand fallo para simbolo {}, devolviendo vacio: {}", symbol, e.getMessage());
      return List.of();
    }
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
        !"INDEX".equals(dto.assetType()) && !"FOREX".equals(dto.assetType()));
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
