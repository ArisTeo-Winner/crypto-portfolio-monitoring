package com.mx.cryptomonitor.integration.marketdata;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;
import com.mx.cryptomonitor.integration.user.support.UserModuleIntegrationTest;

/**
 * End-to-end: registro de un CETES via /me/transactions/buy y valuacion a mercado via
 * /portfolio/cetes/{id}/mark-to-market, incluyendo aislamiento por usuario y validacion de tipo de
 * activo. Banxico no es alcanzable en este entorno de test, asi que la valuacion degrada a "no
 * disponible" (200 con valorHoy=null) sin lanzar excepcion.
 */
class CetesPositionControllerIT extends UserModuleIntegrationTest {

  @Test
  void getMarkToMarketForOwnCetesTransactionReturnsOk() throws Exception {
    Tokens tokens = registerAndLogin();
    String transactionId = buyCetes(tokens, "2030-01-01");

    mockMvc
        .perform(
            get("/api/v1/portfolio/cetes/{id}/mark-to-market", transactionId)
                .header("Authorization", "Bearer " + tokens.accessToken()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.valorCompra").value(17990.74))
        .andExpect(jsonPath("$.vencida").value(false));
  }

  @Test
  void getMarkToMarketForAnotherUsersTransactionReturnsNotFound() throws Exception {
    Tokens ownerTokens = registerAndLogin();
    String transactionId = buyCetes(ownerTokens, "2030-01-01");

    Tokens otherUserTokens = registerAndLogin();

    mockMvc
        .perform(
            get("/api/v1/portfolio/cetes/{id}/mark-to-market", transactionId)
                .header("Authorization", "Bearer " + otherUserTokens.accessToken()))
        .andExpect(status().isNotFound());
  }

  @Test
  void getMarkToMarketForNonCetesTransactionReturnsBadRequest() throws Exception {
    Tokens tokens = registerAndLogin();
    String transactionId = buyStock(tokens);

    mockMvc
        .perform(
            get("/api/v1/portfolio/cetes/{id}/mark-to-market", transactionId)
                .header("Authorization", "Bearer " + tokens.accessToken()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void getMarkToMarketWithoutTokenIsUnauthorized() throws Exception {
    mockMvc
        .perform(get("/api/v1/portfolio/cetes/{id}/mark-to-market", UUID.randomUUID()))
        .andExpect(status().isUnauthorized());
  }

  private String buyCetes(Tokens tokens, String maturityDate) throws Exception {
    String body =
        """
        {
          "assetSymbol":      "CETES91",
          "assetType":        "GOVERNMENT_BOND",
          "quantity":         1,
          "pricePerUnit":     17990.74,
          "faceValue":        18524.32,
          "couponRate":       11.38,
          "maturityDate":     "%s",
          "currency":         "MXN",
          "broker":           "cetesdirecto",
          "transactionDate":  "2023-06-26T10:00:00Z"
        }
        """
            .formatted(maturityDate);

    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/me/transactions/buy")
                    .header("Authorization", "Bearer " + tokens.accessToken())
                    .header("X-Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.transactionId");
  }

  private String buyStock(Tokens tokens) throws Exception {
    String body =
        """
        {
          "assetSymbol":     "AAPL",
          "assetType":       "STOCK",
          "quantity":        3,
          "pricePerUnit":    190.00,
          "transactionDate": "2026-06-10T09:30:00Z"
        }
        """;

    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/me/transactions/buy")
                    .header("Authorization", "Bearer " + tokens.accessToken())
                    .header("X-Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn();
    return JsonPath.read(result.getResponse().getContentAsString(), "$.transactionId");
  }
}
