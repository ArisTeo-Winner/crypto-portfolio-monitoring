package com.mx.cryptomonitor.integration.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import com.mx.cryptomonitor.marketdata.application.port.in.ReplayDividendAccrualUseCase;
import com.mx.cryptomonitor.marketdata.application.port.out.DividendEventData;
import com.mx.cryptomonitor.marketdata.application.port.out.DividendProviderPort;
import com.mx.cryptomonitor.marketdata.application.service.DividendSyncService;
import com.mx.cryptomonitor.transaction.domain.model.Transaction;
import com.mx.cryptomonitor.transaction.domain.repository.TransactionRepository;

/**
 * Regresión (fix idempotencia huérfana): cuando se borra la COMPRA de un activo con dividendos
 * AUTO-acreditados, la reconciliación elimina esos dividendos ACCRUED; si NO olvida su registro de
 * idempotencia (llave {@code accrual:dividend:...}, TTL 7 días), el registro huérfano apunta a una
 * transacción ya borrada y bloquea la re-acreditación: al re-registrar el activo el accrual intenta
 * reutilizar la respuesta vieja, no la encuentra y falla en silencio — el usuario deja de ver
 * dividendos que sí le corresponden.
 *
 * <p>Flujo end-to-end sobre Postgres + Redis reales (el proveedor Massive se mockea para controlar
 * el payload): acreditar → borrar la compra (reconciliación borra el dividendo) → re-registrar la
 * compra → replay de accrual (ADR-0027). Con el fix, el dividendo vuelve a acreditarse.
 */
class DividendReaccrualAfterDeleteIT extends UserModuleIntegrationTest {

  private static final String SYMBOL = "RCONA";
  private static final LocalDate EX_DATE = LocalDate.of(2024, 6, 10);

  @MockitoBean private DividendProviderPort dividendProvider;

  @Autowired private DividendSyncService dividendSyncService;
  @Autowired private ReplayDividendAccrualUseCase replayDividendAccrualUseCase;
  @Autowired private TransactionRepository transactionRepository;

  @Test
  void reaccruesDividendAfterDeletingAndReregisteringTheHolding() throws Exception {
    Tokens tokens = registerAndLogin();

    // El proveedor reporta un dividendo con ex-date 2024-06-10.
    when(dividendProvider.fetchDividends(SYMBOL))
        .thenReturn(
            List.of(
                new DividendEventData(
                    SYMBOL,
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
                    "E-rcona-reaccrual")));

    // 1) Compra 100 títulos antes de la ex-date -> tiene derecho.
    buy(tokens, "100");

    // 2) La detección acredita el dividendo al tenedor.
    assertThat(dividendSyncService.sync(SYMBOL)).isEqualTo(1);
    assertThat(userDividends()).hasSize(1);

    // 3) Borra la COMPRA -> la reconciliación borra el dividendo ACCRUED (entitlement cae a 0) y,
    //    con el fix, olvida su llave de idempotencia huérfana.
    UUID buyId = userBuy().getTransactionId();
    deleteTransaction(tokens, buyId);
    assertThat(userDividends()).isEmpty();

    // 4) Re-registra la COMPRA (vuelve el entitlement). El evento de mercado sigue persistido.
    buy(tokens, "100");

    // 5) Replay de accrual (ADR-0027): re-emite el dividendo ya detectado.
    replayDividendAccrualUseCase.replayForAccrual(SYMBOL);

    // 6) El dividendo se RE-acredita -> el historial del usuario vuelve a mostrarlo.
    //    Sin el fix, la llave huérfana haría fallar el accrual y esto quedaría vacío.
    List<Transaction> dividends = userDividends();
    assertThat(dividends).hasSize(1);
    assertThat(dividends.get(0).getTotalValue()).isEqualByComparingTo("20"); // 100 x 0.20 bruto
  }

  private List<Transaction> userDividends() {
    return transactionRepository.findAll().stream()
        .filter(t -> "DIVIDEND".equals(t.getTransactionType()))
        .filter(t -> SYMBOL.equals(t.getAssetSymbol()))
        .toList();
  }

  private Transaction userBuy() {
    return transactionRepository.findAll().stream()
        .filter(t -> "BUY".equals(t.getTransactionType()))
        .filter(t -> SYMBOL.equals(t.getAssetSymbol()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("No se encontró la compra de " + SYMBOL));
  }

  private void buy(Tokens tokens, String quantity) throws Exception {
    String body =
        """
        {
          "assetSymbol":     "%s",
          "assetName":       "Recon Test Security",
          "assetType":       "STOCK",
          "quantity":        %s,
          "pricePerUnit":    140.00,
          "exchange":        "NASDAQGS",
          "broker":          "DriveWealth",
          "currency":        "USD",
          "transactionDate": "2024-01-15T09:30:00Z"
        }
        """
            .formatted(SYMBOL, quantity);

    mockMvc
        .perform(
            post("/api/v1/me/transactions/buy")
                .header("Authorization", "Bearer " + tokens.accessToken())
                .header("X-Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isCreated());
  }

  private void deleteTransaction(Tokens tokens, UUID transactionId) throws Exception {
    mockMvc
        .perform(
            delete("/api/v1/me/transactions/{id}", transactionId)
                .header("Authorization", "Bearer " + tokens.accessToken())
                .header("X-Idempotency-Key", UUID.randomUUID().toString()))
        .andExpect(status().isNoContent());
  }
}
