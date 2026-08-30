package com.mx.cryptomonitor.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(
    properties = {"springdoc.api-docs.enabled=true", "springdoc.swagger-ui.enabled=true"})
class OpenApiTransactionDocumentationIT {

  @Autowired private MockMvc mockMvc;

  @Test
  void openApiShouldExposeTransactionExamplesSchemasAndProblemResponses() throws Exception {
    String openApi =
        mockMvc
            .perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    assertThat(openApi).contains("/api/v1/me/transactions/buy");
    assertThat(openApi).contains("/api/v1/me/transactions/sell");
    assertThat(openApi).contains("/api/v1/me/transactions/transfer");
    assertThat(openApi).contains("/api/v1/me/transactions/details/{transactionId}");

    assertThat(openApi).contains("\"BuyTransactionRequest\"");
    assertThat(openApi).contains("\"SellTransactionRequest\"");
    assertThat(openApi).contains("\"TransferTransactionRequest\"");
    assertThat(openApi).contains("\"TransactionDetailsResponse\"");
    assertThat(openApi).contains("\"TransactionResponse\"");

    assertThat(openApi).contains("Compra manual");
    assertThat(openApi).contains("Venta parcial");
    assertThat(openApi).contains("Transferencia desde Bitget");
    assertThat(openApi).contains("Total Spent");

    assertThat(openApi).contains("application/problem+json");
    assertThat(openApi).contains("Payload invalido");
    assertThat(openApi).contains("Usuario no autenticado");
    assertThat(openApi).contains("Usuario no autorizado");
  }

  @Test
  void openApiShouldExposeFrictionBreakdownAndBrokerImportEndpoints() throws Exception {
    String openApi =
        mockMvc
            .perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

    // Desglose de friccion expuesto en el detalle (consumido por el frontend)
    assertThat(openApi).contains("\"FrictionBreakdownView\"");
    assertThat(openApi).contains("frictionBreakdown");
    assertThat(openApi).contains("adjustedUnitPrice");
    // reviewStatus como enum tipado, no string libre
    assertThat(openApi).contains("REQUIERE_REVISION");

    // Endpoints de import (incluye auto-routing) y su respuesta de job
    assertThat(openApi).contains("/api/v1/me/broker/gbm/import");
    assertThat(openApi).contains("/api/v1/me/broker/gbm/import-jobs/{jobId}");
    assertThat(openApi).contains("\"StatementImportJobResponse\"");

    // Seguridad JWT documentada para que el cliente generado sepa autenticarse
    assertThat(openApi).contains("bearerAuth");
  }

  @Test
  void openApiMarksSecuredEndpointsAndLeavesPublicOnesOpen() throws Exception {
    String openApi =
        mockMvc
            .perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode api = new ObjectMapper().readTree(openApi);

    // Endpoint admin (protegido, fuera de /me/**) ahora SI declara bearerAuth
    JsonNode rolesSecurity = api.at("/paths/~1api~1v1~1roles/get/security");
    assertThat(rolesSecurity.isMissingNode()).isFalse();
    assertThat(rolesSecurity.toString()).contains("bearerAuth");

    // Endpoint publico (login) NO declara seguridad
    JsonNode loginSecurity = api.at("/paths/~1api~1v1~1auth~1login/post/security");
    assertThat(loginSecurity.isMissingNode()).isTrue();
  }
}
