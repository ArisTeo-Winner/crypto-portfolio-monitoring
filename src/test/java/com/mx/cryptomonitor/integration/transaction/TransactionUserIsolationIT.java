package com.mx.cryptomonitor.integration.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;
import com.mx.cryptomonitor.integration.user.support.UserModuleIntegrationTest;

/**
 * TEST 6 — aislamiento multi-tenant: un usuario no ve transacciones de otro, y las claves de
 * idempotencia están aisladas por userId.
 */
class TransactionUserIsolationIT extends UserModuleIntegrationTest {

  // ── TEST 6a — usuario B no ve las transacciones de usuario A ─────────────

  @Test
  void userBCannotSeeUserATransactions() throws Exception {
    Tokens userA = registerAndLogin();
    Tokens userB = registerAndLogin();

    // User A crea una transacción
    mockMvc
        .perform(
            post("/api/v1/me/transactions/buy")
                .header("Authorization", "Bearer " + userA.accessToken())
                .header("X-Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "assetSymbol":     "BTC",
                      "assetType":       "CRYPTO",
                      "quantity":        0.5,
                      "pricePerUnit":    90000.00,
                      "transactionDate": "2026-06-10T10:00:00Z"
                    }
                    """))
        .andExpect(status().isCreated());

    // User B lista sus transacciones — no debe ver la de A
    String responseB =
        mockMvc
            .perform(
                get("/api/v1/me/transactions")
                    .header("Authorization", "Bearer " + userB.accessToken()))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat((int) JsonPath.read(responseB, "$.length()"))
        .as("User B must see 0 transactions; User A's BTC purchase must not be visible")
        .isZero();
  }

  @Test
  void userBSeesOnlyOwnTransactionsWhenBothHaveRecords() throws Exception {
    Tokens userA = registerAndLogin();
    Tokens userB = registerAndLogin();

    // User A crea una transacción en AAPL
    mockMvc
        .perform(
            post("/api/v1/me/transactions/buy")
                .header("Authorization", "Bearer " + userA.accessToken())
                .header("X-Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "assetSymbol":     "AAPL",
                      "assetType":       "STOCK",
                      "quantity":        5,
                      "pricePerUnit":    180.00,
                      "transactionDate": "2026-06-10T10:00:00Z"
                    }
                    """))
        .andExpect(status().isCreated());

    // User B crea su propia transacción en ETH
    var resultB =
        mockMvc
            .perform(
                post("/api/v1/me/transactions/buy")
                    .header("Authorization", "Bearer " + userB.accessToken())
                    .header("X-Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "assetSymbol":     "ETH",
                          "assetType":       "CRYPTO",
                          "quantity":        1,
                          "pricePerUnit":    3000.00,
                          "transactionDate": "2026-06-10T11:00:00Z"
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    String bTxId = JsonPath.read(resultB.getResponse().getContentAsString(), "$.transactionId");

    // User B ve exactamente 1 transacción y es la suya (ETH)
    String responseB =
        mockMvc
            .perform(
                get("/api/v1/me/transactions")
                    .header("Authorization", "Bearer " + userB.accessToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].transactionId").value(bTxId))
            .andExpect(jsonPath("$[0].assetSymbol").value("ETH"))
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Ninguna de las transacciones pertenece al símbolo de A
    assertThat(responseB).doesNotContain("AAPL");
  }

  // ── TEST 6b — misma X-Idempotency-Key en usuarios distintos no colisiona ──

  @Test
  void sameIdempotencyKeyForDifferentUsersCreatesSeparateTransactions() throws Exception {
    Tokens userA = registerAndLogin();
    Tokens userB = registerAndLogin();

    String sharedKey = "shared-idempotency-key-" + UUID.randomUUID();

    // User A usa la key para AAPL
    var resultA =
        mockMvc
            .perform(
                post("/api/v1/me/transactions/buy")
                    .header("Authorization", "Bearer " + userA.accessToken())
                    .header("X-Idempotency-Key", sharedKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "assetSymbol":     "AAPL",
                          "assetType":       "STOCK",
                          "quantity":        3,
                          "pricePerUnit":    190.00,
                          "transactionDate": "2026-06-10T09:00:00Z"
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    // User B usa la misma key para un payload distinto (BTC) — no debe colisionar
    var resultB =
        mockMvc
            .perform(
                post("/api/v1/me/transactions/buy")
                    .header("Authorization", "Bearer " + userB.accessToken())
                    .header("X-Idempotency-Key", sharedKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "assetSymbol":     "BTC",
                          "assetType":       "CRYPTO",
                          "quantity":        0.1,
                          "pricePerUnit":    89000.00,
                          "transactionDate": "2026-06-10T09:00:00Z"
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    String txIdA = JsonPath.read(resultA.getResponse().getContentAsString(), "$.transactionId");
    String txIdB = JsonPath.read(resultB.getResponse().getContentAsString(), "$.transactionId");

    assertThat(txIdA)
        .as(
            "La misma X-Idempotency-Key no debe producir el mismo transactionId para usuarios distintos")
        .isNotEqualTo(txIdB);
  }
}
