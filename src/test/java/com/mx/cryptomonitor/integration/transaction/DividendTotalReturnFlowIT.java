package com.mx.cryptomonitor.integration.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import com.mx.cryptomonitor.integration.user.support.UserModuleIntegrationTest;
import com.mx.cryptomonitor.portfolio.application.dto.response.PortfolioHoldingsPerformanceResponse;
import com.mx.cryptomonitor.portfolio.application.port.in.PortfolioQueryPort;
import com.mx.cryptomonitor.transaction.domain.repository.TransactionRepository;

/**
 * ADR-0014 F4 end-to-end: una transaccion DIVIDEND con retencion fluye al Total Return a traves del
 * stack real (DividendDetail -> TransactionHistoryAdapter JPQL batch -> snapshot.taxWithheld ->
 * acumulador de income neto). Valida en vivo el {@code findTaxWithheldByUserId} contra Postgres.
 */
class DividendTotalReturnFlowIT extends UserModuleIntegrationTest {

  @Autowired private PortfolioQueryPort portfolioQueryPort;
  @Autowired private TransactionRepository transactionRepository;

  @Test
  void netDividendIncomeFeedsTotalReturn() throws Exception {
    Tokens tokens = registerAndLogin();

    buyGoogl(tokens);
    postDividend(tokens, "20.00", "3.80"); // bruto 20, retencion 3.80 -> neto 16.20

    UUID userId =
        transactionRepository.findAll().stream()
            .filter(t -> "GOOGL".equals(t.getAssetSymbol()))
            .findFirst()
            .orElseThrow()
            .getUser()
            .getId();

    PortfolioHoldingsPerformanceResponse response =
        portfolioQueryPort.getHoldingsPerformanceByPortfolioId(userId, "ALL", "ALL");

    // Income neto = 20 - 3.80 (independiente del precio vivo).
    assertThat(response.dividendIncome()).isEqualByComparingTo("16.20");
    // Total Return = plusvalia + income neto (invariante relacional, cualquiera sea el precio).
    assertThat(response.totalReturn())
        .isEqualByComparingTo(response.allTimeProfit().add(response.dividendIncome()));
  }

  private void buyGoogl(Tokens tokens) throws Exception {
    String body =
        """
        {
          "assetSymbol":    "GOOGL",
          "assetName":      "Alphabet Inc.",
          "assetType":      "STOCK",
          "quantity":       100,
          "pricePerUnit":   140.00,
          "exchange":       "NASDAQGS",
          "broker":         "DriveWealth",
          "currency":       "USD",
          "transactionDate": "2024-01-15T09:30:00Z"
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

  private void postDividend(Tokens tokens, String amount, String taxWithheld) throws Exception {
    String body =
        """
        {
          "assetSymbol":     "GOOGL",
          "assetType":       "STOCK",
          "amount":          %s,
          "transactionDate": "2024-06-17T00:00:00Z",
          "exDividendDate":  "2024-06-10",
          "dividendType":    "CASH",
          "taxWithheld":     %s,
          "currency":        "USD"
        }
        """
            .formatted(amount, taxWithheld);
    mockMvc
        .perform(
            post("/api/v1/me/transactions/dividend")
                .header("Authorization", "Bearer " + tokens.accessToken())
                .header("X-Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated());
  }
}
