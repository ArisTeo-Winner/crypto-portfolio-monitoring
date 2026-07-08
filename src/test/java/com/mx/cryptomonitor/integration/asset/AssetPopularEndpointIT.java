package com.mx.cryptomonitor.integration.asset;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.mx.cryptomonitor.integration.user.support.UserModuleIntegrationTest;

class AssetPopularEndpointIT extends UserModuleIntegrationTest {

  @Autowired private StringRedisTemplate redisTemplate;

  @BeforeEach
  void clearCatalog() {
    Set<String> keys = redisTemplate.keys("catalog:*");
    if (keys != null && !keys.isEmpty()) {
      redisTemplate.delete(keys);
    }
  }

  @Test
  void returnsPopularAssetsForAllTypesWhenRedisHasData() throws Exception {
    loadEntry("NVDA", "NVIDIA Corporation", "STOCK");
    loadEntry("AAPL", "Apple Inc.", "STOCK");
    loadEntry("VOO", "Vanguard S&P 500 ETF", "ETF");
    loadEntry("QQQ", "Invesco QQQ Trust", "ETF");
    loadEntry("BTC", "Bitcoin", "CRYPTO");
    loadEntry("ETH", "Ethereum", "CRYPTO");

    redisTemplate.opsForZSet().add("catalog:top10:stock", "NVDA", 4967d);
    redisTemplate.opsForZSet().add("catalog:top10:stock", "AAPL", 4514d);
    redisTemplate.opsForZSet().add("catalog:top10:etf", "VOO", 600d);
    redisTemplate.opsForZSet().add("catalog:top10:etf", "QQQ", 550d);
    redisTemplate.opsForZSet().add("catalog:top10:crypto", "BTC", 2100d);
    redisTemplate.opsForZSet().add("catalog:top10:crypto", "ETH", 320d);

    mockMvc
        .perform(get("/api/v1/assets/popular"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stocks").isArray())
        .andExpect(jsonPath("$.etfs").isArray())
        .andExpect(jsonPath("$.cryptos").isArray())
        .andExpect(jsonPath("$.stocks[0].symbol").value("NVDA"))
        .andExpect(jsonPath("$.etfs[0].symbol").value("VOO"))
        .andExpect(jsonPath("$.cryptos[0].symbol").value("BTC"));
  }

  @Test
  void returnsEmptyListsWhenRedisIsEmpty() throws Exception {
    mockMvc
        .perform(get("/api/v1/assets/popular"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stocks").isEmpty())
        .andExpect(jsonPath("$.etfs").isEmpty())
        .andExpect(jsonPath("$.cryptos").isEmpty());
  }

  @Test
  void endpointIsPublicAndDoesNotRequireToken() throws Exception {
    mockMvc.perform(get("/api/v1/assets/popular")).andExpect(status().isOk());
  }

  @Test
  void stocksAndEtfsHaveCompaniesLogoUrlWhileCryptosDoNot() throws Exception {
    loadEntry(
        "NVDA",
        "NVIDIA Corporation",
        "STOCK",
        "https://companieslogo.com/api/starter/stock-symbol/NVDA");
    loadEntry(
        "VOO",
        "Vanguard S&P 500 ETF",
        "ETF",
        "https://companieslogo.com/api/starter/stock-symbol/VOO");
    loadEntry("BTC", "Bitcoin", "CRYPTO", "");

    redisTemplate.opsForZSet().add("catalog:top10:stock", "NVDA", 4967d);
    redisTemplate.opsForZSet().add("catalog:top10:etf", "VOO", 600d);
    redisTemplate.opsForZSet().add("catalog:top10:crypto", "BTC", 2100d);

    mockMvc
        .perform(get("/api/v1/assets/popular"))
        .andExpect(status().isOk())
        .andExpect(
            jsonPath("$.stocks[0].logoUrl")
                .value(
                    org.hamcrest.Matchers.containsString(
                        "companieslogo.com/api/starter/stock-symbol/")))
        .andExpect(
            jsonPath("$.etfs[0].logoUrl")
                .value(org.hamcrest.Matchers.containsString("companieslogo.com")))
        .andExpect(jsonPath("$.cryptos[0].logoUrl").value(org.hamcrest.Matchers.nullValue()));
  }

  private void loadEntry(String symbol, String name, String assetType) {
    loadEntry(symbol, name, assetType, "");
  }

  private void loadEntry(String symbol, String name, String assetType, String logoUrl) {
    redisTemplate
        .opsForHash()
        .putAll(
            "catalog:entry:" + symbol,
            Map.of(
                "symbol",
                symbol,
                "name",
                name,
                "assetType",
                assetType,
                "logoUrl",
                logoUrl,
                "exchange",
                "",
                "currency",
                "USD",
                "marketCap",
                "0",
                "updatedAt",
                "2026-06-10T00:00:00Z"));
  }
}
