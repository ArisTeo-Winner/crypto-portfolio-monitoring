package com.mx.cryptomonitor.integration.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
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

  // ── TEST 3b — GOVERNMENT_BOND (CETES) con campos de bono ─────────────────

  @Test
  void buyGovernmentBondPersistsBondSpecificFields() throws Exception {
    Tokens tokens = registerAndLogin();

    String body =
        """
        {
          "assetSymbol":      "CETES91",
          "assetType":        "GOVERNMENT_BOND",
          "quantity":         1,
          "pricePerUnit":     17990.74,
          "faceValue":        18524.32,
          "couponRate":       11.38,
          "maturityDate":     "2023-09-26",
          "autoReinvestment": true,
          "currency":         "MXN",
          "broker":           "cetesdirecto",
          "transactionDate":  "2023-06-26T10:00:00Z"
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
            .andReturn();

    String txId = JsonPath.read(result.getResponse().getContentAsString(), "$.transactionId");
    Transaction tx = transactionRepository.findById(UUID.fromString(txId)).orElseThrow();

    assertThat(tx.getFaceValue()).isEqualByComparingTo(new BigDecimal("18524.32"));
    assertThat(tx.getMaturityDate()).isEqualTo(LocalDate.of(2023, 9, 26));
    assertThat(tx.getCouponRate()).isEqualByComparingTo(new BigDecimal("11.38"));
    assertThat(tx.isAutoReinvestment()).isTrue();
    assertThat(tx.getCurrency()).isEqualTo("MXN");
    assertThat(tx.getBroker()).isEqualTo("cetesdirecto");
  }

  // ── TEST 3c — venta STOCK en BMV (Bursanet) con exchange y currency ───────

  @Test
  void sellStockOnBmvPersistsExchangeAndCurrency() throws Exception {
    Tokens tokens = registerAndLogin();

    // Require a prior BUY so the portfolio has sufficient holdings to sell.
    mockMvc
        .perform(
            post("/api/v1/me/transactions/buy")
                .header("Authorization", "Bearer " + tokens.accessToken())
                .header("X-Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "assetSymbol":     "AAPLSTAR",
                      "assetType":       "STOCK",
                      "quantity":        10,
                      "pricePerUnit":    4800.00,
                      "exchange":        "BMV",
                      "broker":          "Bursanet",
                      "currency":        "MXN",
                      "transactionDate": "2026-06-09T09:30:00Z"
                    }
                    """))
        .andExpect(status().isCreated());

    var result =
        mockMvc
            .perform(
                post("/api/v1/me/transactions/sell")
                    .header("Authorization", "Bearer " + tokens.accessToken())
                    .header("X-Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "assetSymbol":     "AAPLSTAR",
                          "assetType":       "STOCK",
                          "quantity":        10,
                          "pricePerUnit":    4949.10,
                          "fee":             123.73,
                          "exchange":        "BMV",
                          "broker":          "Bursanet",
                          "currency":        "MXN",
                          "transactionDate": "2026-06-10T09:30:00Z"
                        }
                        """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.exchange").value("BMV"))
            .andExpect(jsonPath("$.currency").value("MXN"))
            .andReturn();

    String txId = JsonPath.read(result.getResponse().getContentAsString(), "$.transactionId");
    Transaction tx = transactionRepository.findById(UUID.fromString(txId)).orElseThrow();
    assertThat(tx.getExchange()).isEqualTo("BMV");
    assertThat(tx.getCurrency()).isEqualTo("MXN");
    assertThat(tx.getBroker()).isEqualTo("Bursanet");
  }

  // ── TEST 3d — campos opcionales omitidos por defecto son null/false ────────

  @Test
  void buyWithOptionalFieldsOmittedDefaultsToNullAndFalse() throws Exception {
    Tokens tokens = registerAndLogin();

    String body =
        """
        {
          "assetSymbol":     "ETH",
          "assetType":       "CRYPTO",
          "quantity":        0.1,
          "pricePerUnit":    3000.00,
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
            .andReturn();

    String txId = JsonPath.read(result.getResponse().getContentAsString(), "$.transactionId");
    Transaction tx = transactionRepository.findById(UUID.fromString(txId)).orElseThrow();

    assertThat(tx.getExchange()).isNull();
    assertThat(tx.getBroker()).isNull();
    assertThat(tx.getCurrency()).isNull();
    assertThat(tx.getFaceValue()).isNull();
    assertThat(tx.getMaturityDate()).isNull();
    assertThat(tx.getCouponRate()).isNull();
    assertThat(tx.isAutoReinvestment()).isFalse();
  }
}
