package com.mx.cryptomonitor.integration.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.jayway.jsonpath.JsonPath;
import com.mx.cryptomonitor.integration.user.support.UserModuleIntegrationTest;
import com.mx.cryptomonitor.transaction.domain.model.DividendDetail;
import com.mx.cryptomonitor.transaction.domain.repository.DividendDetailRepository;
import com.mx.cryptomonitor.transaction.domain.repository.TransactionRepository;

// FmpCatalogAdapter uses fmp.base-url=http://localhost (test profile) — calls fail immediately,
// CatalogWarmUpRunner catches the error and continues without affecting test execution.
class DividendTransactionIT extends UserModuleIntegrationTest {

  @Autowired private DividendDetailRepository dividendDetailRepository;
  @Autowired private TransactionRepository transactionRepository;

  @Test
  void cashDividendCreatesTransactionAndPersistsAllFields() throws Exception {
    Tokens tokens = registerAndLogin();
    String idempotencyKey = UUID.randomUUID().toString();

    String body =
        """
        {
          "assetSymbol":     "AAPL",
          "assetName":       "Apple Inc.",
          "assetType":       "STOCK",
          "amount":          125.50,
          "transactionDate": "2026-06-10T00:00:00Z",
          "exDividendDate":  "2026-05-09",
          "dividendType":    "CASH",
          "taxWithheld":     37.65,
          "broker":          "IBKR",
          "currency":        "USD"
        }
        """;

    var result =
        mockMvc
            .perform(
                post("/api/v1/me/transactions/dividend")
                    .header("Authorization", "Bearer " + tokens.accessToken())
                    .header("X-Idempotency-Key", idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.transactionType").value("DIVIDEND"))
            .andExpect(jsonPath("$.totalValue").value(125.50))
            .andReturn();

    String transactionId =
        JsonPath.read(result.getResponse().getContentAsString(), "$.transactionId");
    DividendDetail detail =
        dividendDetailRepository
            .findByTransactionTransactionId(UUID.fromString(transactionId))
            .orElseThrow();
    assertThat(detail.getDividendType().name()).isEqualTo("CASH");
    assertThat(detail.getExDividendDate()).isEqualTo(LocalDate.of(2026, 5, 9));
    assertThat(detail.getTaxWithheld()).isEqualByComparingTo(new BigDecimal("37.65"));
  }

  @Test
  void stockDividendCreatesDetailWithStockType() throws Exception {
    Tokens tokens = registerAndLogin();

    String body =
        """
        {
          "assetSymbol":     "MSFT",
          "assetType":       "STOCK",
          "amount":          100.00,
          "transactionDate": "2026-06-10T12:00:00Z",
          "dividendType":    "STOCK"
        }
        """;

    var result =
        mockMvc
            .perform(
                post("/api/v1/me/transactions/dividend")
                    .header("Authorization", "Bearer " + tokens.accessToken())
                    .header("X-Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.transactionType").value("DIVIDEND"))
            .andReturn();

    String transactionId =
        JsonPath.read(result.getResponse().getContentAsString(), "$.transactionId");
    DividendDetail detail =
        dividendDetailRepository
            .findByTransactionTransactionId(UUID.fromString(transactionId))
            .orElseThrow();
    assertThat(detail.getDividendType().name()).isEqualTo("STOCK");
  }

  @Test
  void dividendWithOptionalFieldsOmittedPersistsNulls() throws Exception {
    Tokens tokens = registerAndLogin();

    String body =
        """
        {
          "assetSymbol":     "T",
          "assetType":       "STOCK",
          "amount":          20.00,
          "transactionDate": "2026-06-10T00:00:00Z"
        }
        """;

    var result =
        mockMvc
            .perform(
                post("/api/v1/me/transactions/dividend")
                    .header("Authorization", "Bearer " + tokens.accessToken())
                    .header("X-Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn();

    String transactionId =
        JsonPath.read(result.getResponse().getContentAsString(), "$.transactionId");
    DividendDetail detail =
        dividendDetailRepository
            .findByTransactionTransactionId(UUID.fromString(transactionId))
            .orElseThrow();
    assertThat(detail.getExDividendDate()).isNull();
    assertThat(detail.getTaxWithheld()).isNull();
  }

  @Test
  void dividendTypeDefaultsToCashWhenOmitted() throws Exception {
    Tokens tokens = registerAndLogin();

    String body =
        """
        {
          "assetSymbol":     "VOO",
          "assetType":       "ETF",
          "amount":          25.00,
          "transactionDate": "2026-06-10T00:00:00Z"
        }
        """;

    var result =
        mockMvc
            .perform(
                post("/api/v1/me/transactions/dividend")
                    .header("Authorization", "Bearer " + tokens.accessToken())
                    .header("X-Idempotency-Key", UUID.randomUUID().toString())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn();

    String transactionId =
        JsonPath.read(result.getResponse().getContentAsString(), "$.transactionId");
    DividendDetail detail =
        dividendDetailRepository
            .findByTransactionTransactionId(UUID.fromString(transactionId))
            .orElseThrow();
    assertThat(detail.getDividendType().name()).isEqualTo("CASH");
  }

  @Test
  void negativeAmountReturns400() throws Exception {
    Tokens tokens = registerAndLogin();

    String body =
        """
        {
          "assetSymbol":     "AAPL",
          "assetType":       "STOCK",
          "amount":          -50.00,
          "transactionDate": "2026-06-10T00:00:00Z"
        }
        """;

    mockMvc
        .perform(
            post("/api/v1/me/transactions/dividend")
                .header("Authorization", "Bearer " + tokens.accessToken())
                .header("X-Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void emptyAssetSymbolReturns400() throws Exception {
    Tokens tokens = registerAndLogin();

    String body =
        """
        {
          "assetSymbol":     "",
          "assetType":       "STOCK",
          "amount":          10.00,
          "transactionDate": "2026-06-10T00:00:00Z"
        }
        """;

    mockMvc
        .perform(
            post("/api/v1/me/transactions/dividend")
                .header("Authorization", "Bearer " + tokens.accessToken())
                .header("X-Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void dividendIsIdempotentWithSameKey() throws Exception {
    Tokens tokens = registerAndLogin();
    String idempotencyKey = UUID.randomUUID().toString();

    String body =
        """
        {
          "assetSymbol":     "AAPL",
          "assetType":       "STOCK",
          "amount":          50.00,
          "transactionDate": "2026-06-10T00:00:00Z"
        }
        """;

    var result1 =
        mockMvc
            .perform(
                post("/api/v1/me/transactions/dividend")
                    .header("Authorization", "Bearer " + tokens.accessToken())
                    .header("X-Idempotency-Key", idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn();

    var result2 =
        mockMvc
            .perform(
                post("/api/v1/me/transactions/dividend")
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
            .filter(
                t ->
                    "DIVIDEND".equals(t.getTransactionType())
                        && "AAPL".equals(t.getAssetSymbol())
                        && new BigDecimal("50.00").compareTo(t.getTotalValue()) == 0)
            .count();
    assertThat(count).isEqualTo(1);
  }

  @Test
  void missingIdempotencyKeyHeaderReturns400() throws Exception {
    Tokens tokens = registerAndLogin();

    String body =
        """
        {
          "assetSymbol":     "AAPL",
          "assetType":       "STOCK",
          "amount":          10.00,
          "transactionDate": "2026-06-10T00:00:00Z"
        }
        """;

    mockMvc
        .perform(
            post("/api/v1/me/transactions/dividend")
                .header("Authorization", "Bearer " + tokens.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isBadRequest());
  }

  // ── TEST 4g — ON DELETE CASCADE via @OnDelete en DividendDetail ──────────

  @Test
  void deletingTransactionCascadesDividendDetail() throws Exception {
    Tokens tokens = registerAndLogin();
    String idempotencyKey = UUID.randomUUID().toString();

    String body =
        """
        {
          "assetSymbol":     "MSFT",
          "assetType":       "STOCK",
          "amount":          75.00,
          "transactionDate": "2026-06-10T00:00:00Z",
          "dividendType":    "CASH"
        }
        """;

    var result =
        mockMvc
            .perform(
                post("/api/v1/me/transactions/dividend")
                    .header("Authorization", "Bearer " + tokens.accessToken())
                    .header("X-Idempotency-Key", idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn();

    UUID transactionId =
        UUID.fromString(
            JsonPath.read(result.getResponse().getContentAsString(), "$.transactionId"));

    assertThat(dividendDetailRepository.findByTransactionTransactionId(transactionId))
        .as("dividend_detail must exist before delete")
        .isPresent();

    mockMvc
        .perform(
            delete("/api/v1/me/transactions/" + transactionId)
                .header("Authorization", "Bearer " + tokens.accessToken())
                .header("X-Idempotency-Key", UUID.randomUUID().toString()))
        .andExpect(status().isNoContent());

    assertThat(dividendDetailRepository.findByTransactionTransactionId(transactionId))
        .as("dividend_detail must be gone after parent transaction is deleted (ON DELETE CASCADE)")
        .isEmpty();
  }

  @Test
  void unauthenticatedReturns401() throws Exception {
    String body =
        """
        {
          "assetSymbol":     "AAPL",
          "assetType":       "STOCK",
          "amount":          10.00,
          "transactionDate": "2026-06-10T00:00:00Z"
        }
        """;

    mockMvc
        .perform(
            post("/api/v1/me/transactions/dividend")
                .header("X-Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isUnauthorized());
  }
}
