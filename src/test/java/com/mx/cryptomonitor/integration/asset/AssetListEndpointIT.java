package com.mx.cryptomonitor.integration.asset;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.mx.cryptomonitor.asset.domain.model.AssetCatalogEntity;
import com.mx.cryptomonitor.asset.domain.repository.AssetCatalogRepository;
import com.mx.cryptomonitor.integration.user.support.UserModuleIntegrationTest;

class AssetListEndpointIT extends UserModuleIntegrationTest {

  @Autowired private StringRedisTemplate redisTemplate;
  @Autowired private AssetCatalogRepository assetCatalogRepository;

  @BeforeEach
  void clearCatalog() {
    Set<String> keys = redisTemplate.keys("catalog:*");
    if (keys != null && !keys.isEmpty()) {
      redisTemplate.delete(keys);
    }
    assetCatalogRepository.deleteAll();
  }

  @Test
  void listByTypeReturnsOnlyStocksFromPostgresFallback() throws Exception {
    seedEntity("AAPL", "Apple Inc.", "STOCK");
    seedEntity("VOO", "Vanguard S&P 500 ETF", "ETF");
    seedEntity("BTC", "Bitcoin", "CRYPTO");

    mockMvc
        .perform(get("/api/v1/assets").param("type", "STOCK"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(1)))
        .andExpect(jsonPath("$[0].symbol").value("AAPL"))
        .andExpect(jsonPath("$[0].assetType").value("STOCK"));
  }

  @Test
  void listByTypeReturnsOnlyEtfs() throws Exception {
    seedEntity("AAPL", "Apple Inc.", "STOCK");
    seedEntity("VOO", "Vanguard S&P 500 ETF", "ETF");
    seedEntity("QQQ", "Invesco QQQ Trust", "ETF");

    mockMvc
        .perform(get("/api/v1/assets").param("type", "ETF"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)))
        .andExpect(
            jsonPath(
                "$[*].assetType",
                org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.equalTo("ETF"))));
  }

  @Test
  void listByTypePrefersRedisOverPostgresWhenPresent() throws Exception {
    seedEntity("BTC", "Bitcoin (stale postgres row)", "CRYPTO");
    redisTemplate
        .opsForHash()
        .putAll(
            "catalog:entry:BTC",
            Map.of(
                "symbol", "BTC",
                "name", "Bitcoin",
                "assetType", "CRYPTO",
                "logoUrl", "",
                "exchange", "",
                "currency", "USD",
                "marketCap", "0",
                "updatedAt", "2026-06-10T00:00:00Z"));
    redisTemplate.opsForZSet().add("catalog:search:crypto", "BTC", 1d);

    mockMvc
        .perform(get("/api/v1/assets").param("type", "CRYPTO"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].symbol").value("BTC"))
        .andExpect(jsonPath("$[0].name").value("Bitcoin"));
  }

  @Test
  void invalidTypeReturns400() throws Exception {
    mockMvc
        .perform(get("/api/v1/assets").param("type", "INVALIDO"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void noTypeReturnsFullCatalog() throws Exception {
    seedEntity("AAPL", "Apple Inc.", "STOCK");
    seedEntity("VOO", "Vanguard S&P 500 ETF", "ETF");
    seedEntity("BTC", "Bitcoin", "CRYPTO");

    mockMvc
        .perform(get("/api/v1/assets"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(3)));
  }

  @Test
  void limitOutOfRangeReturns400() throws Exception {
    mockMvc.perform(get("/api/v1/assets").param("limit", "500")).andExpect(status().isBadRequest());
  }

  @Test
  void endpointIsPublicAndDoesNotRequireToken() throws Exception {
    mockMvc.perform(get("/api/v1/assets")).andExpect(status().isOk());
  }

  private void seedEntity(String symbol, String name, String assetType) {
    assetCatalogRepository.save(
        AssetCatalogEntity.builder()
            .symbol(symbol)
            .name(name)
            .assetType(assetType)
            .popular(false)
            .updatedAt(OffsetDateTime.parse("2026-06-10T00:00:00Z"))
            .build());
  }
}
