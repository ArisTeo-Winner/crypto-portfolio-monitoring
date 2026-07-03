package com.mx.cryptomonitor.integration.asset;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;

import com.mx.cryptomonitor.asset.application.dto.AssetCatalogDto;
import com.mx.cryptomonitor.asset.application.port.out.CatalogStorePort;
import com.mx.cryptomonitor.integration.user.support.UserModuleIntegrationTest;

/**
 * Verifica la resolucion automatica de {@code assetName} desde el catalogo cuando el request de
 * compra no lo incluye (cierra A7). Cobertura equivalente y mas detallada ya existe en {@code
 * BuyTransactionNewFieldsIT#buyWithoutAssetNameResolvesItFromRedis} y {@code
 * #buyWithoutAssetNameAndSymbolNotInRedisLeavesAssetNameNull}; esta clase se mantiene como
 * verificacion dedicada y trazable para el catalogo (PROMPT B).
 */
class AssetNameResolutionIT extends UserModuleIntegrationTest {

  @Autowired private StringRedisTemplate redisTemplate;
  @Autowired private CatalogStorePort catalogStorePort;

  @BeforeEach
  void clearCatalog() {
    Set<String> keys = redisTemplate.keys("catalog:*");
    if (keys != null && !keys.isEmpty()) {
      redisTemplate.delete(keys);
    }
  }

  @Test
  void buyWithoutAssetNameResolvesItFromCatalog() throws Exception {
    catalogStorePort.saveEntry(
        new AssetCatalogDto("VOO", "Vanguard S&P 500 ETF", "ETF", null, "NYSE", "USD", null));

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
  void buyWithoutAssetNameAndUnknownSymbolSucceedsWithNullAssetName() throws Exception {
    Tokens tokens = registerAndLogin();

    String body =
        """
        {
          "assetSymbol":    "GRNY",
          "assetType":      "STOCK",
          "quantity":       10,
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
}
