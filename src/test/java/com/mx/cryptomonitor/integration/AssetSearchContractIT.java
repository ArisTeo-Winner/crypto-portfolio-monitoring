package com.mx.cryptomonitor.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AssetSearchContractIT {

  @Autowired private MockMvc mockMvc;

  @Test
  void shouldReturnAssetsForSelector() throws Exception {
    mockMvc
        .perform(get("/api/v1/assets/search").param("q", "btc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.query").value("btc"))
        .andExpect(jsonPath("$.total").value(1))
        .andExpect(jsonPath("$.items[0].assetId").value("bitcoin"))
        .andExpect(jsonPath("$.items[0].symbol").value("BTC"))
        .andExpect(jsonPath("$.items[0].name").value("Bitcoin"))
        .andExpect(jsonPath("$.items[0].assetType").value("CRYPTO"))
        .andExpect(jsonPath("$.items[0].logoUrl").isEmpty())
        .andExpect(jsonPath("$.items[0].supportedForTransactions").value(true));
  }

  @Test
  void shouldReturn400WhenQueryIsBlank() throws Exception {
    mockMvc
        .perform(get("/api/v1/assets/search").param("q", " "))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.title").value("Invalid Parameters"))
        .andExpect(jsonPath("$.detail").value("Query parameter q is required."))
        .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.errors").isArray());
  }

  @Test
  void shouldReturn429WhenAssetSearchRateLimitIsExceeded() throws Exception {
    String clientIp = "203.0.113.10";

    for (int attempt = 0; attempt < 30; attempt++) {
      mockMvc
          .perform(
              get("/api/v1/assets/search").param("q", "btc").header("X-Forwarded-For", clientIp))
          .andExpect(status().isOk());
    }

    mockMvc
        .perform(get("/api/v1/assets/search").param("q", "btc").header("X-Forwarded-For", clientIp))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists("Retry-After"))
        .andExpect(jsonPath("$.title").value("Too Many Requests"))
        .andExpect(jsonPath("$.errorCode").value("ASSET_SEARCH_RATE_LIMIT_EXCEEDED"))
        .andExpect(jsonPath("$.errors").isArray());
  }
}
