package com.mx.cryptomonitor.integration.transaction;

import static org.assertj.core.api.Assertions.assertThat;
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

import com.jayway.jsonpath.JsonPath;
import com.mx.cryptomonitor.integration.user.support.UserModuleIntegrationTest;
import com.mx.cryptomonitor.transaction.domain.model.Transaction;
import com.mx.cryptomonitor.transaction.domain.repository.TransactionRepository;

class BuyTransactionNewFieldsIT extends UserModuleIntegrationTest {

  @Autowired private TransactionRepository transactionRepository;
  @Autowired private StringRedisTemplate redisTemplate;

  @BeforeEach
  void clearCatalog() {
    Set<String> keys = redisTemplate.keys("catalog:*");
    if (keys != null && !keys.isEmpty()) {
      redisTemplate.delete(keys);
    }
  }

  @Test
  void buyWithAllNewFieldsPersistsThemToDb() throws Exception {
    Tokens tokens = registerAndLogin();

    String body =
        """
        {
          "assetSymbol":    "NVDA",
          "assetName":      "NVIDIA Corporation",
          "assetType":      "STOCK",
          "quantity":       10,
          "pricePerUnit":   205.10,
          "exchange":       "NASDAQGS",
          "broker":         "IBKR",
          "currency":       "USD",
          "transactionDate": "2026-06-10T09:30:00Z"
        }
        """;

    var result =
        mockMvc
            .perform(
                post("/api/v1/me/transactions/buy")
                    .header("Authorization", "Bearer " + tokens.accessToken())
                    .header("X-Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.assetName").value("NVIDIA Corporation"))
            .andExpect(jsonPath("$.exchange").value("NASDAQGS"))
            .andExpect(jsonPath("$.broker").value("IBKR"))
            .andExpect(jsonPath("$.currency").value("USD"))
            .andReturn();

    String transactionId =
        JsonPath.read(result.getResponse().getContentAsString(), "$.transactionId");
    Transaction tx = transactionRepository.findById(UUID.fromString(transactionId)).orElseThrow();
    assertThat(tx.getAssetName()).isEqualTo("NVIDIA Corporation");
    assertThat(tx.getExchange()).isEqualTo("NASDAQGS");
    assertThat(tx.getBroker()).isEqualTo("IBKR");
    assertThat(tx.getCurrency()).isEqualTo("USD");
  }

  @Test
  void buyWithoutAssetNameResolvesItFromRedis() throws Exception {
    redisTemplate
        .opsForHash()
        .putAll(
            "catalog:entry:VOO",
            Map.of(
                "symbol", "VOO",
                "name", "Vanguard S&P 500 ETF",
                "assetType", "ETF",
                "logoUrl", "",
                "exchange", "",
                "currency", "USD",
                "marketCap", "0",
                "updatedAt", "2026-06-10T00:00:00Z"));

    Tokens tokens = registerAndLogin();

    String body =
        """
        {
          "assetSymbol":    "VOO",
          "assetType":      "ETF",
          "quantity":       5,
          "pricePerUnit":   490.00,
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
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.assetName").value("Vanguard S&P 500 ETF"));
  }

  @Test
  void buyWithoutAssetNameAndSymbolNotInRedisLeavesAssetNameNull() throws Exception {
    Tokens tokens = registerAndLogin();

    String body =
        """
        {
          "assetSymbol":    "GRNY",
          "assetType":      "STOCK",
          "quantity":       100,
          "pricePerUnit":   5.00,
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
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.assetName").value((Object) null));
  }

  @Test
  void buyIsIdempotentWithSameKey() throws Exception {
    Tokens tokens = registerAndLogin();
    String idempotencyKey = UUID.randomUUID().toString();

    String body =
        """
        {
          "assetSymbol":    "AAPL",
          "assetName":      "Apple Inc.",
          "assetType":      "STOCK",
          "quantity":       3,
          "pricePerUnit":   190.00,
          "transactionDate": "2026-06-10T09:30:00Z"
        }
        """;

    var result1 =
        mockMvc
            .perform(
                post("/api/v1/me/transactions/buy")
                    .header("Authorization", "Bearer " + tokens.accessToken())
                    .header("X-Idempotency-Key", idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn();

    var result2 =
        mockMvc
            .perform(
                post("/api/v1/me/transactions/buy")
                    .header("Authorization", "Bearer " + tokens.accessToken())
                    .header("X-Idempotency-Key", idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn();

    String id1 = JsonPath.read(result1.getResponse().getContentAsString(), "$.transactionId");
    String id2 = JsonPath.read(result2.getResponse().getContentAsString(), "$.transactionId");
    assertThat(id1).isEqualTo(id2);

    long count =
        transactionRepository.findAll().stream()
            .filter(t -> "BUY".equals(t.getTransactionType()) && "AAPL".equals(t.getAssetSymbol()))
            .count();
    assertThat(count).isEqualTo(1);
  }
}
