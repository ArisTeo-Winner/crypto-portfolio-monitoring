package com.mx.cryptomonitor.integration.asset;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.mx.cryptomonitor.asset.application.dto.AssetCatalogDto;
import com.mx.cryptomonitor.asset.application.port.out.CatalogFetchPort;
import com.mx.cryptomonitor.asset.application.port.out.CatalogStorePort;
import com.mx.cryptomonitor.asset.domain.exception.CatalogPlanRestrictedException;
import com.mx.cryptomonitor.integration.user.support.UserModuleIntegrationTest;

class AssetSearchEndpointIT extends UserModuleIntegrationTest {

  @Autowired private StringRedisTemplate redisTemplate;
  @Autowired private CatalogStorePort catalogStorePort;
  @MockBean private CatalogFetchPort catalogFetchPort;

  @BeforeEach
  void clearCatalog() {
    Set<String> keys = redisTemplate.keys("catalog:*");
    if (keys != null && !keys.isEmpty()) {
      redisTemplate.delete(keys);
    }
  }

  @Test
  void searchFindsStockEntryFromRedis() throws Exception {
    redisTemplate
        .opsForHash()
        .putAll(
            "catalog:entry:NVDA",
            Map.of(
                "symbol", "NVDA",
                "name", "NVIDIA Corporation",
                "assetType", "STOCK",
                "logoUrl", "",
                "exchange", "NASDAQGS",
                "currency", "USD",
                "marketCap", "2400000000000",
                "updatedAt", "2026-06-10T00:00:00Z"));
    redisTemplate.opsForZSet().add("catalog:search:stock", "NVDA", 4967d);

    mockMvc
        .perform(get("/api/v1/assets/search").param("q", "NVI"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].symbol").value("NVDA"))
        .andExpect(jsonPath("$.items[0].name").value("NVIDIA Corporation"))
        .andExpect(jsonPath("$.items[0].assetType").value("STOCK"))
        .andExpect(jsonPath("$.items[0].supportedForTransactions").value(true));
  }

  @Test
  void searchEtfReturnsLogoUrlFromCompaniesLogo() throws Exception {
    redisTemplate
        .opsForHash()
        .putAll(
            "catalog:entry:VOO",
            Map.of(
                "symbol", "VOO",
                "name", "Vanguard S&P 500 ETF",
                "assetType", "ETF",
                "logoUrl", "https://companieslogo.com/api/starter/stock-symbol/VOO",
                "exchange", "NYSE",
                "currency", "USD",
                "marketCap", "0",
                "updatedAt", "2026-06-10T00:00:00Z"));
    redisTemplate.opsForZSet().add("catalog:search:etf", "VOO", 600d);

    mockMvc
        .perform(get("/api/v1/assets/search").param("q", "VOO"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].symbol").value("VOO"))
        .andExpect(jsonPath("$.items[0].assetType").value("ETF"))
        .andExpect(
            jsonPath("$.items[0].logoUrl")
                .value(org.hamcrest.Matchers.containsString("companieslogo.com")));
  }

  @Test
  void searchFindsGovernmentBondEntryPreloadedViaCatalogStorePort() throws Exception {
    AssetCatalogDto cetes91 =
        new AssetCatalogDto(
            "CETES91", "CETES 91 dias", "GOVERNMENT_BOND", null, "cetesdirecto", "MXN", null);
    catalogStorePort.saveEntry(cetes91);
    catalogStorePort.addToRanking("catalog:search:government_bond", "CETES91", 1d);

    mockMvc
        .perform(get("/api/v1/assets/search").param("q", "CETES"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].symbol").value("CETES91"))
        .andExpect(jsonPath("$.items[0].assetType").value("GOVERNMENT_BOND"))
        .andExpect(jsonPath("$.items[0].supportedForTransactions").value(true))
        .andExpect(jsonPath("$.items[0].logoUrl").value(org.hamcrest.Matchers.nullValue()));
  }

  @Test
  void searchStockReturnsExactCompaniesLogoUrl() throws Exception {
    redisTemplate
        .opsForHash()
        .putAll(
            "catalog:entry:AAPL",
            Map.of(
                "symbol", "AAPL",
                "name", "Apple Inc.",
                "assetType", "STOCK",
                "logoUrl", "https://companieslogo.com/api/starter/stock-symbol/AAPL",
                "exchange", "NASDAQ",
                "currency", "USD",
                "marketCap", "3000000000000",
                "updatedAt", "2026-06-10T00:00:00Z"));
    redisTemplate.opsForZSet().add("catalog:search:stock", "AAPL", 3_000_000d);

    mockMvc
        .perform(get("/api/v1/assets/search").param("q", "AAPL"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].symbol").value("AAPL"))
        .andExpect(
            jsonPath("$.items[0].logoUrl")
                .value("https://companieslogo.com/api/starter/stock-symbol/AAPL"));
  }

  @Test
  void searchGovernmentBondUsdReturnsCompaniesLogoUrl() throws Exception {
    AssetCatalogDto tlt =
        new AssetCatalogDto(
            "TLT",
            "iShares 20+ Year Treasury Bond ETF",
            "GOVERNMENT_BOND",
            "https://companieslogo.com/api/starter/stock-symbol/TLT",
            null,
            "USD",
            null);
    catalogStorePort.saveEntry(tlt);
    catalogStorePort.addToRanking("catalog:search:government_bond", "TLT", 1d);

    mockMvc
        .perform(get("/api/v1/assets/search").param("q", "TLT"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].symbol").value("TLT"))
        .andExpect(
            jsonPath("$.items[0].logoUrl")
                .value(org.hamcrest.Matchers.containsString("companieslogo.com")));
  }

  @Test
  void searchCryptoReturnsNullLogoUrl() throws Exception {
    AssetCatalogDto btc =
        new AssetCatalogDto("BTC", "Bitcoin", "CRYPTO", null, null, "USD", 1_900_000L);
    catalogStorePort.saveEntry(btc);
    catalogStorePort.addToRanking("catalog:search:crypto", "BTC", 1_900_000d);

    mockMvc
        .perform(get("/api/v1/assets/search").param("q", "BTC"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].symbol").value("BTC"))
        .andExpect(jsonPath("$.items[0].logoUrl").value(org.hamcrest.Matchers.nullValue()));
  }

  @Test
  void searchIndexReturnsSupportedForTransactionsFalse() throws Exception {
    redisTemplate
        .opsForHash()
        .putAll(
            "catalog:entry:^GSPC",
            Map.of(
                "symbol", "^GSPC",
                "name", "S&P 500",
                "assetType", "INDEX",
                "logoUrl", "",
                "exchange", "",
                "currency", "USD",
                "marketCap", "0",
                "updatedAt", "2026-06-10T00:00:00Z"));
    redisTemplate.opsForZSet().add("catalog:search:stock", "^GSPC", 100d);

    mockMvc
        .perform(get("/api/v1/assets/search").param("q", "GSPC"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].supportedForTransactions").value(false));
  }

  @Test
  void searchWithNoRedisHitsReturnsEmptyList() throws Exception {
    mockMvc
        .perform(get("/api/v1/assets/search").param("q", "GRNY"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isEmpty())
        .andExpect(jsonPath("$.total").value(0));
  }

  @Test
  void searchReturnsEmptyItemsInsteadOf500WhenFmpFailsPermanently() throws Exception {
    when(catalogFetchPort.fetchTopStocks(anyInt()))
        .thenThrow(new CatalogPlanRestrictedException("fetchTopStocks: endpoint restricted"));

    mockMvc
        .perform(get("/api/v1/assets/search").param("q", "XYZ"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items").isEmpty())
        .andExpect(jsonPath("$.total").value(0));
  }

  @Test
  void emptyQueryReturns400() throws Exception {
    mockMvc
        .perform(get("/api/v1/assets/search").param("q", ""))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.detail").isNotEmpty());
  }

  @Test
  void limitOutOfRangeReturns400() throws Exception {
    mockMvc
        .perform(get("/api/v1/assets/search").param("q", "NVI").param("limit", "99"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void searchIsCaseInsensitive() throws Exception {
    redisTemplate
        .opsForHash()
        .putAll(
            "catalog:entry:NVDA",
            Map.of(
                "symbol", "NVDA",
                "name", "NVIDIA Corporation",
                "assetType", "STOCK",
                "logoUrl", "",
                "exchange", "NASDAQGS",
                "currency", "USD",
                "marketCap", "2400000000000",
                "updatedAt", "2026-06-10T00:00:00Z"));
    redisTemplate.opsForZSet().add("catalog:search:stock", "NVDA", 4967d);

    mockMvc
        .perform(get("/api/v1/assets/search").param("q", "nvda"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].symbol").value("NVDA"));
  }
}
