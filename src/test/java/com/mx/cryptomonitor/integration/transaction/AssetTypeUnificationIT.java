package com.mx.cryptomonitor.integration.transaction;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;

import com.mx.cryptomonitor.integration.user.support.UserModuleIntegrationTest;

class AssetTypeUnificationIT extends UserModuleIntegrationTest {

  @Autowired private StringRedisTemplate redisTemplate;

  @BeforeEach
  void clearCatalog() {
    Set<String> keys = redisTemplate.keys("catalog:*");
    if (keys != null && !keys.isEmpty()) {
      redisTemplate.delete(keys);
    }
  }

  @Test
  void governmentBondAssetTypeIsAcceptedForBuyTransaction() throws Exception {
    Tokens tokens = registerAndLogin();

    String body =
        """
        {
          "assetSymbol":    "TLT",
          "assetType":      "GOVERNMENT_BOND",
          "quantity":       10,
          "pricePerUnit":   95.00,
          "transactionDate": "2026-06-10T09:30:00Z"
        }
        """;

    mockMvc
        .perform(
            post("/api/v1/me/transactions/buy")
                .header("Authorization", "Bearer " + tokens.accessToken())
                .header("X-Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated());
  }

  @Test
  void indexAssetTypeIsRejectedForBuyTransaction() throws Exception {
    Tokens tokens = registerAndLogin();

    String body =
        """
        {
          "assetSymbol":    "SPY",
          "assetType":      "INDEX",
          "quantity":       1,
          "pricePerUnit":   500.00,
          "transactionDate": "2026-06-10T09:30:00Z"
        }
        """;

    mockMvc
        .perform(
            post("/api/v1/me/transactions/buy")
                .header("Authorization", "Bearer " + tokens.accessToken())
                .header("X-Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void legacyBonosValueIsRejectedForBuyTransaction() throws Exception {
    Tokens tokens = registerAndLogin();

    String body =
        """
        {
          "assetSymbol":    "TLT",
          "assetType":      "BONOS",
          "quantity":       10,
          "pricePerUnit":   95.00,
          "transactionDate": "2026-06-10T09:30:00Z"
        }
        """;

    mockMvc
        .perform(
            post("/api/v1/me/transactions/buy")
                .header("Authorization", "Bearer " + tokens.accessToken())
                .header("X-Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void searchReturnsEnglishAssetTypeForBondEntry() throws Exception {
    redisTemplate
        .opsForHash()
        .putAll(
            "catalog:entry:TLT",
            Map.of(
                "symbol", "TLT",
                "name", "iShares 20+ Year Treasury Bond ETF",
                "assetType", "BOND",
                "logoUrl", "",
                "exchange", "NASDAQ",
                "currency", "USD",
                "marketCap", "0",
                "updatedAt", "2026-06-10T00:00:00Z"));
    redisTemplate.opsForZSet().add("catalog:search:etf", "TLT", 300d);

    mockMvc
        .perform(get("/api/v1/assets/search").param("q", "TLT"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].assetType").value("BOND"));
  }
}
