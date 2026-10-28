package com.mx.cryptomonitor.integration.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.mx.cryptomonitor.integration.user.support.UserModuleIntegrationTest;
import com.mx.cryptomonitor.marketdata.application.port.out.DividendEventData;
import com.mx.cryptomonitor.marketdata.application.port.out.DividendProviderPort;
import com.mx.cryptomonitor.marketdata.application.service.DividendSyncService;
import com.mx.cryptomonitor.transaction.domain.model.DividendDetail;
import com.mx.cryptomonitor.transaction.domain.model.Transaction;
import com.mx.cryptomonitor.transaction.domain.repository.DividendDetailRepository;
import com.mx.cryptomonitor.transaction.domain.repository.TransactionRepository;

/**
 * ADR-0014 F3c end-to-end: la deteccion de un dividendo (sync) publica el evento, el listener
 * acredita por usuario con la cadena fiscal US-&gt;MX y el binomio ADR-0013. El proveedor Massive
 * se mockea para controlar el payload; el resto del flujo (evento, listener, alta, persistencia) es
 * real sobre Postgres + Redis.
 */
class DividendAccrualFlowIT extends UserModuleIntegrationTest {

  @MockitoBean private DividendProviderPort dividendProvider;

  @Autowired private DividendSyncService dividendSyncService;
  @Autowired private TransactionRepository transactionRepository;
  @Autowired private DividendDetailRepository dividendDetailRepository;

  private static final LocalDate EX_DATE = LocalDate.of(2024, 6, 10);

  @Test
  void detectedDividendAccruesToHolderWithTaxChain() throws Exception {
    Tokens tokens = registerAndLogin();

    // El usuario compra 100 GOOGL antes de la ex-date -> tiene derecho.
    buyGoogl(tokens, "100", "2024-01-15T09:30:00Z");

    when(dividendProvider.fetchDividends("GOOGL"))
        .thenReturn(
            List.of(
                new DividendEventData(
                    "GOOGL",
                    EX_DATE,
                    EX_DATE,
                    LocalDate.of(2024, 6, 17),
                    LocalDate.of(2024, 4, 25),
                    new BigDecimal("0.20"),
                    "USD",
                    (short) 4,
                    "recurring",
                    new BigDecimal("0.20"),
                    new BigDecimal("0.994165"),
                    "E1cdc29fc58066")));

    int detected = dividendSyncService.sync("GOOGL");
    assertThat(detected).isEqualTo(1);

    List<Transaction> dividends =
        transactionRepository.findAll().stream()
            .filter(t -> "DIVIDEND".equals(t.getTransactionType()))
            .filter(t -> "GOOGL".equals(t.getAssetSymbol()))
            .toList();

    assertThat(dividends).hasSize(1);
    Transaction dividend = dividends.get(0);
    assertThat(dividend.getTotalValue()).isEqualByComparingTo("20"); // 100 x 0.20 bruto
    assertThat(dividend.getExecutionZoneId()).isEqualTo("America/New_York");

    DividendDetail detail =
        dividendDetailRepository
            .findByTransactionTransactionId(dividend.getTransactionId())
            .orElseThrow();
    assertThat(detail.getExDividendDate()).isEqualTo(EX_DATE);
    assertThat(detail.getTaxWithheld()).isEqualByComparingTo("3.8"); // 2.00 US + 1.80 MX
    assertThat(detail.getDividendType().name()).isEqualTo("CASH");

    // Idempotencia: re-sincronizar no duplica el accrual (evento ya almacenado).
    assertThat(dividendSyncService.sync("GOOGL")).isZero();
    long count =
        transactionRepository.findAll().stream()
            .filter(t -> "DIVIDEND".equals(t.getTransactionType()))
            .filter(t -> "GOOGL".equals(t.getAssetSymbol()))
            .count();
    assertThat(count).isEqualTo(1);
  }

  @Test
  void dividendDoesNotAccrueToUserWhoBoughtOnExDate() throws Exception {
    Tokens tokens = registerAndLogin();

    // Comprar el mismo dia ex NO da derecho (regla < estricta de ADR-0013). Simbolo propio para no
    // colisionar por la llave natural con el otro test (estado compartido entre metodos).
    buy(tokens, "AMZN", "50", "2024-06-10T09:30:00Z");

    when(dividendProvider.fetchDividends("AMZN"))
        .thenReturn(
            List.of(
                new DividendEventData(
                    "AMZN",
                    EX_DATE,
                    EX_DATE,
                    LocalDate.of(2024, 6, 17),
                    LocalDate.of(2024, 4, 25),
                    new BigDecimal("0.20"),
                    "USD",
                    (short) 4,
                    "recurring",
                    null,
                    null,
                    "E-exday")));

    dividendSyncService.sync("AMZN");

    long count =
        transactionRepository.findAll().stream()
            .filter(t -> "DIVIDEND".equals(t.getTransactionType()))
            .filter(t -> "AMZN".equals(t.getAssetSymbol()))
            .count();
    assertThat(count).isZero();
  }

  private void buyGoogl(Tokens tokens, String quantity, String isoDate) throws Exception {
    buy(tokens, "GOOGL", quantity, isoDate);
  }

  private void buy(Tokens tokens, String symbol, String quantity, String isoDate) throws Exception {
    String body =
        """
        {
          "assetSymbol":    "%s",
          "assetName":      "Test Security",
          "assetType":      "STOCK",
          "quantity":       %s,
          "pricePerUnit":   140.00,
          "exchange":       "NASDAQGS",
          "broker":         "DriveWealth",
          "currency":       "USD",
          "transactionDate": "%s"
        }
        """
            .formatted(symbol, quantity, isoDate);

    mockMvc
        .perform(
            post("/api/v1/me/transactions/buy")
                .header("Authorization", "Bearer " + tokens.accessToken())
                .header("X-Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated());
  }
}
