package com.mx.cryptomonitor.integration.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.JsonNode;
import com.mx.cryptomonitor.integration.user.support.UserModuleIntegrationTest;
import com.mx.cryptomonitor.transaction.application.dto.request.BuyTransactionRequest;
import com.mx.cryptomonitor.transaction.application.dto.request.SellTransactionRequest;

/**
 * Regresión: fee con 8 decimales (0.12345678) debe conservarse sin pérdida de precisión a lo largo
 * de todo el ciclo HTTP → Service → Postgres (NUMERIC 18,8) → HTTP, y el modal de detalle (GET
 * /details/{id}) debe calcular netAmount con aritmética BigDecimal.
 *
 * <p>Cada test va contra la pila completa con Testcontainers (PostgreSQL + Redis).
 */
@DisplayName("Regresión: precisión de fee a 8 decimales en transacciones")
class TransactionFeeHighPrecisionIT extends UserModuleIntegrationTest {

  private static final BigDecimal FEE = new BigDecimal("0.12345678");

  // -------------------------------------------------------------------------
  // Helpers
  // -------------------------------------------------------------------------

  /** Extrae el id de la respuesta de creación y lo devuelve como UUID. */
  private UUID transactionIdFrom(JsonNode body) {
    return UUID.fromString(body.get("transactionId").asText());
  }

  /** Llama a GET /details/{id} con el token indicado y devuelve el nodo JSON. */
  private JsonNode fetchDetail(UUID transactionId, String accessToken) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                get("/api/v1/me/transactions/details/" + transactionId)
                    .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andReturn();
    return objectMapper.readTree(result.getResponse().getContentAsString());
  }

  // -------------------------------------------------------------------------
  // Tests
  // -------------------------------------------------------------------------

  @Test
  @DisplayName(
      "BUY — fee=0.12345678 se serializa, persiste y devuelve sin truncamiento en la respuesta"
          + " de creación")
  void compraBtc_feeOchoDecimales_persisteIntactoEnRespuestaCreacion() throws Exception {
    Tokens tokens = registerAndLogin();

    BuyTransactionRequest request =
        new BuyTransactionRequest(
            "BTC",
            "CRYPTO",
            new BigDecimal("2.5"),
            new BigDecimal("45000.00000000"),
            FEE,
            LocalDateTime.now(),
            "regresion-fee-precision-buy");

    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/me/transactions/buy")
                    .header("Authorization", "Bearer " + tokens.accessToken())
                    .header("X-Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.assetSymbol").value("BTC"))
            .andExpect(jsonPath("$.transactionType").value("BUY"))
            .andReturn();

    JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());

    // fee debe sobrevivir JSON → Jackson → MapStruct → Postgres NUMERIC(18,8) → respuesta
    assertThat(response.get("fee").decimalValue())
        .as("fee en respuesta de creación debe conservar los 8 decimales originales")
        .isEqualByComparingTo(FEE);
  }

  @Test
  @DisplayName(
      "BUY — modal de detalle: netAmount = grossAmount + fee con precisión completa de 8 decimales")
  void compraBtc_feeOchoDecimales_modalMuestraNetAmountSumaExacta() throws Exception {
    Tokens tokens = registerAndLogin();

    // quantity=2.5, pricePerUnit=45000 → grossAmount=112500
    // netAmount(BUY) = 112500 + 0.12345678 = 112500.12345678
    BuyTransactionRequest request =
        new BuyTransactionRequest(
            "BTC",
            "CRYPTO",
            new BigDecimal("2.5"),
            new BigDecimal("45000.00000000"),
            FEE,
            LocalDateTime.now(),
            "regresion-fee-precision-buy-modal");

    MvcResult createResult =
        mockMvc
            .perform(
                post("/api/v1/me/transactions/buy")
                    .header("Authorization", "Bearer " + tokens.accessToken())
                    .header("X-Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();

    UUID transactionId =
        transactionIdFrom(objectMapper.readTree(createResult.getResponse().getContentAsString()));

    JsonNode detail = fetchDetail(transactionId, tokens.accessToken());

    BigDecimal fee = detail.get("fee").decimalValue();
    BigDecimal grossAmount = detail.get("grossAmount").decimalValue();
    BigDecimal netAmount = detail.get("netAmount").decimalValue();

    assertThat(fee)
        .as("fee en el modal de detalle debe mantener 8 decimales")
        .isEqualByComparingTo(FEE);

    assertThat(grossAmount)
        .as("grossAmount = quantity × pricePerUnit")
        .isEqualByComparingTo(new BigDecimal("112500"));

    // Verificación clave: BigDecimal produce 112500.12345678 exacto.
    // Con double, la suma daría un resultado con error de punto flotante.
    assertThat(netAmount)
        .as("netAmount(BUY) = grossAmount + fee debe ser 112500.12345678 exacto")
        .isEqualByComparingTo(new BigDecimal("112500.12345678"));

    assertThat(detail.get("amountLabel").asText()).isEqualTo("Total Spent");
  }

  @Test
  @DisplayName(
      "SELL — modal de detalle: netAmount = grossAmount − fee con cancelación decimal exacta"
          + " (caso sensible a double)")
  void ventaEth_feeOchoDecimales_modalMuestraNetAmountRestaExacta() throws Exception {
    Tokens tokens = registerAndLogin();

    // Primero se necesita una posición existente de ETH para poder vender.
    // Se compra 5 ETH a un precio arbitrario; lo importante es tener saldo suficiente.
    BuyTransactionRequest buyRequest =
        new BuyTransactionRequest(
            "ETH",
            "CRYPTO",
            new BigDecimal("5.00000000"),
            new BigDecimal("50000.12345678"),
            BigDecimal.ZERO,
            LocalDateTime.now(),
            "setup-eth-position-for-sell-test");

    mockMvc
        .perform(
            post("/api/v1/me/transactions/buy")
                .header("Authorization", "Bearer " + tokens.accessToken())
                .header("X-Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buyRequest)))
        .andExpect(status().isCreated());

    // Caso diseñado para fallar con double aritmético:
    //   grossAmount = 1.00000000 × 50000.12345678 = 50000.12345678
    //   netAmount(SELL) = 50000.12345678 − 0.12345678 = 50000.00000000 exacto
    // Con double: 50000.12345678 - 0.12345678 = 50000.000000007276... (error de representación)
    SellTransactionRequest request =
        new SellTransactionRequest(
            "ETH",
            "CRYPTO",
            new BigDecimal("1.00000000"),
            new BigDecimal("50000.12345678"),
            FEE,
            LocalDateTime.now(),
            "regresion-fee-precision-sell-modal");

    MvcResult createResult =
        mockMvc
            .perform(
                post("/api/v1/me/transactions/sell")
                    .header("Authorization", "Bearer " + tokens.accessToken())
                    .header("X-Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();

    JsonNode createBody = objectMapper.readTree(createResult.getResponse().getContentAsString());

    assertThat(createBody.get("fee").decimalValue())
        .as("fee en respuesta de creación SELL")
        .isEqualByComparingTo(FEE);

    UUID transactionId = transactionIdFrom(createBody);
    JsonNode detail = fetchDetail(transactionId, tokens.accessToken());

    BigDecimal fee = detail.get("fee").decimalValue();
    BigDecimal grossAmount = detail.get("grossAmount").decimalValue();
    BigDecimal netAmount = detail.get("netAmount").decimalValue();

    assertThat(fee).as("fee en modal de detalle SELL").isEqualByComparingTo(FEE);

    assertThat(grossAmount)
        .as("grossAmount = 1 × 50000.12345678")
        .isEqualByComparingTo(new BigDecimal("50000.12345678"));

    assertThat(netAmount)
        .as("netAmount(SELL) = 50000.12345678 − 0.12345678 debe ser 50000 exacto")
        .isEqualByComparingTo(new BigDecimal("50000.00000000"));

    assertThat(detail.get("amountLabel").asText()).isEqualTo("Total Received");
  }
}
