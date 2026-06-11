package com.mx.cryptomonitor.unit.asset.infrastructure.outbound.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import com.mx.cryptomonitor.asset.application.dto.AssetCatalogDto;
import com.mx.cryptomonitor.asset.infrastructure.outbound.redis.CatalogRedisAdapter;

@SuppressWarnings("unchecked")
class CatalogRedisAdapterTest {

  private StringRedisTemplate redisTemplate;
  private HashOperations<String, Object, Object> hashOps;
  private ZSetOperations<String, String> zsetOps;
  private CatalogRedisAdapter service;

  @BeforeEach
  void setUp() {
    redisTemplate = mock(StringRedisTemplate.class);
    hashOps = mock(HashOperations.class);
    zsetOps = mock(ZSetOperations.class);
    when(redisTemplate.opsForHash()).thenReturn(hashOps);
    when(redisTemplate.opsForZSet()).thenReturn(zsetOps);
    service = new CatalogRedisAdapter(redisTemplate);
  }

  @Test
  void saveEntryWritesAllFieldsToHash() {
    AssetCatalogDto dto =
        new AssetCatalogDto(
            "AAPL", "Apple Inc.", "STOCK", "https://logo/aapl.png", "NASDAQ", "USD", 3_000_000L);

    service.saveEntry(dto);

    verify(hashOps).putAll(eq("catalog:entry:AAPL"), any(Map.class));
  }

  @Test
  void addToRankingDelegatesToZSet() {
    service.addToRanking("catalog:search:stock", "AAPL", 3_000_000.0);

    verify(zsetOps).add("catalog:search:stock", "AAPL", 3_000_000.0);
  }

  @Test
  void getTopSymbolsReturnsReverseRangeResult() {
    when(zsetOps.reverseRange("catalog:top10:stock", 0, 9))
        .thenReturn(Set.of("AAPL", "MSFT", "NVDA"));

    List<String> result = service.getTopSymbols("catalog:top10:stock", 10);

    assertThat(result).containsExactlyInAnyOrder("AAPL", "MSFT", "NVDA");
  }

  @Test
  void getTopSymbolsReturnsEmptyWhenRedisReturnsNull() {
    when(zsetOps.reverseRange(anyString(), any(Long.class), any(Long.class))).thenReturn(null);

    List<String> result = service.getTopSymbols("catalog:top10:stock", 10);

    assertThat(result).isEmpty();
  }

  @Test
  void findEntryReturnsDtoFromCatalogEntry() {
    Map<Object, Object> fields =
        Map.of(
            "symbol", "ETH",
            "name", "Ethereum",
            "assetType", "CRYPTO",
            "logoUrl", "",
            "exchange", "",
            "currency", "",
            "marketCap", "500000");
    when(hashOps.entries("catalog:entry:ETH")).thenReturn(fields);

    Optional<AssetCatalogDto> result = service.findEntry("ETH");

    assertThat(result).isPresent();
    assertThat(result.get().symbol()).isEqualTo("ETH");
    assertThat(result.get().name()).isEqualTo("Ethereum");
    assertThat(result.get().assetType()).isEqualTo("CRYPTO");
    assertThat(result.get().logoUrl()).isNull();
    assertThat(result.get().marketCap()).isEqualTo(500_000L);
  }

  @Test
  void findEntryFallsBackToMiscKeyWhenCatalogEntryEmpty() {
    when(hashOps.entries("catalog:entry:NEWCOIN")).thenReturn(Map.of());
    Map<Object, Object> miscFields =
        Map.of(
            "symbol", "NEWCOIN",
            "name", "New Coin",
            "assetType", "CRYPTO",
            "logoUrl", "",
            "exchange", "",
            "currency", "",
            "marketCap", "0");
    when(hashOps.entries("catalog:search:misc:NEWCOIN")).thenReturn(miscFields);

    Optional<AssetCatalogDto> result = service.findEntry("NEWCOIN");

    assertThat(result).isPresent();
    assertThat(result.get().name()).isEqualTo("New Coin");
  }

  @Test
  void findEntryReturnsEmptyWhenBothKeysAreEmpty() {
    when(hashOps.entries(anyString())).thenReturn(Map.of());

    Optional<AssetCatalogDto> result = service.findEntry("UNKNOWN");

    assertThat(result).isEmpty();
  }

  @Test
  void isCatalogLoadedReturnsTrueWhenStockRankingHasEntries() {
    when(zsetOps.size("catalog:search:stock")).thenReturn(50L);

    assertThat(service.isCatalogLoaded()).isTrue();
  }

  @Test
  void isCatalogLoadedReturnsFalseWhenStockRankingIsEmpty() {
    when(zsetOps.size("catalog:search:stock")).thenReturn(0L);

    assertThat(service.isCatalogLoaded()).isFalse();
  }

  @Test
  void saveMiscEntryWritesHashAndSetsExpiry() {
    AssetCatalogDto dto = new AssetCatalogDto("XYZ", "XYZ Token", "CRYPTO", null, null, null, null);

    service.saveMiscEntry(dto, Duration.ofHours(24));

    verify(hashOps).putAll(eq("catalog:search:misc:XYZ"), any(Map.class));
    verify(redisTemplate).expire(eq("catalog:search:misc:XYZ"), eq(Duration.ofHours(24)));
  }
}
