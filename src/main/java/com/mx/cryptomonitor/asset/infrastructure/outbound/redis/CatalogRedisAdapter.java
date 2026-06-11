package com.mx.cryptomonitor.asset.infrastructure.outbound.redis;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.mx.cryptomonitor.asset.application.dto.AssetCatalogDto;
import com.mx.cryptomonitor.asset.application.port.out.CatalogStorePort;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CatalogRedisAdapter implements CatalogStorePort {

  private final StringRedisTemplate redisTemplate;

  @Override
  public void saveEntry(AssetCatalogDto entry) {
    String key = "catalog:entry:" + entry.symbol().toUpperCase();
    Map<String, String> fields =
        Map.of(
            "symbol",
            entry.symbol(),
            "name",
            entry.name(),
            "assetType",
            entry.assetType(),
            "logoUrl",
            entry.logoUrl() != null ? entry.logoUrl() : "",
            "exchange",
            entry.exchange() != null ? entry.exchange() : "",
            "currency",
            entry.currency() != null ? entry.currency() : "",
            "marketCap",
            entry.marketCap() != null ? entry.marketCap().toString() : "0",
            "updatedAt",
            OffsetDateTime.now().toString());
    redisTemplate.opsForHash().putAll(key, fields);
  }

  @Override
  public void addToRanking(String rankingKey, String symbol, double score) {
    redisTemplate.opsForZSet().add(rankingKey, symbol, score);
  }

  @Override
  public List<String> getTopSymbols(String rankingKey, int limit) {
    Set<String> result = redisTemplate.opsForZSet().reverseRange(rankingKey, 0, limit - 1);
    return result != null ? new ArrayList<>(result) : List.of();
  }

  @Override
  public Optional<AssetCatalogDto> findEntry(String symbol) {
    String key = "catalog:entry:" + symbol.toUpperCase();
    Map<Object, Object> fields = redisTemplate.opsForHash().entries(key);
    if (fields.isEmpty()) {
      key = "catalog:search:misc:" + symbol.toUpperCase();
      fields = redisTemplate.opsForHash().entries(key);
    }
    return fields.isEmpty() ? Optional.empty() : Optional.of(mapToDto(fields));
  }

  @Override
  public boolean isCatalogLoaded() {
    Long size = redisTemplate.opsForZSet().size("catalog:search:stock");
    return size != null && size > 0;
  }

  @Override
  public void saveMiscEntry(AssetCatalogDto entry, Duration ttl) {
    String key = "catalog:search:misc:" + entry.symbol().toUpperCase();
    Map<String, String> fields =
        Map.of(
            "symbol",
            entry.symbol(),
            "name",
            entry.name(),
            "assetType",
            entry.assetType(),
            "logoUrl",
            entry.logoUrl() != null ? entry.logoUrl() : "",
            "exchange",
            entry.exchange() != null ? entry.exchange() : "",
            "currency",
            entry.currency() != null ? entry.currency() : "",
            "marketCap",
            entry.marketCap() != null ? entry.marketCap().toString() : "0",
            "updatedAt",
            OffsetDateTime.now().toString());
    redisTemplate.opsForHash().putAll(key, fields);
    redisTemplate.expire(key, ttl);
  }

  private AssetCatalogDto mapToDto(Map<Object, Object> fields) {
    return new AssetCatalogDto(
        (String) fields.get("symbol"),
        (String) fields.get("name"),
        (String) fields.get("assetType"),
        nullIfBlank((String) fields.get("logoUrl")),
        nullIfBlank((String) fields.get("exchange")),
        nullIfBlank((String) fields.get("currency")),
        parseLong((String) fields.get("marketCap")));
  }

  private String nullIfBlank(String s) {
    return (s == null || s.isBlank()) ? null : s;
  }

  private Long parseLong(String s) {
    try {
      return s != null ? Long.parseLong(s) : null;
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
