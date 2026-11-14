package com.mx.cryptomonitor.transaction.application.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mx.cryptomonitor.transaction.application.port.in.DividendReconciliationUseCase;
import com.mx.cryptomonitor.transaction.domain.dividend.DividendEntitlementCalculator;
import com.mx.cryptomonitor.transaction.domain.dividend.DividendTaxBreakdown;
import com.mx.cryptomonitor.transaction.domain.dividend.DividendTaxCalculator;
import com.mx.cryptomonitor.transaction.domain.model.DividendDetail;
import com.mx.cryptomonitor.transaction.domain.model.Transaction;
import com.mx.cryptomonitor.transaction.domain.repository.DividendDetailRepository;
import com.mx.cryptomonitor.transaction.domain.repository.TransactionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Re-deriva los dividendos AUTO-acreditados (origin=ACCRUED) de un usuario para un símbolo tras una
 * mutación de la tenencia (SPEC reconciliación al borrar). Por cada dividendo ACCRUED: recalcula el
 * entitlement a su ex-date sobre el historial actual; si la tenencia quedó ≤ 0 lo elimina (ya no
 * hubo derecho), si cambió pero sigue > 0 recalcula el monto (bruto = acciones × DPS). Los MANUAL
 * no se tocan.
 *
 * <p>Servicio aparte (no depende de {@code TransactionCommandUseCase}) a propósito: lo invoca el
 * borrado en {@code TransactionService}, y {@code DividendAccrualService} ya depende de este último
 * — ponerlo aquí evita el ciclo de beans.
 *
 * <p>Al eliminar un dividendo ACCRUED SIEMPRE se olvida también su registro de idempotencia (llave
 * {@code accrual:dividend:...}, vía {@link
 * TransactionIdempotencyService#forgetByResultTransactionId}). Si no, el registro huérfano (apunta
 * a una transacción ya borrada, TTL 7 días) bloquea la re-acreditación: al re-registrar el activo
 * el accrual intenta reutilizar la respuesta vieja, no la encuentra y falla silenciosamente — el
 * usuario deja de ver dividendos que sí le corresponden.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DividendReconciliationService implements DividendReconciliationUseCase {

  private final DividendDetailRepository dividendDetailRepository;
  private final TransactionRepository transactionRepository;
  private final DividendEntitlementCalculator entitlementCalculator;
  private final DividendTaxCalculator taxCalculator;
  private final TransactionIdempotencyService idempotencyService;

  @Override
  @Transactional
  public void reconcileAccruedDividends(UUID userId, String assetSymbol) {
    if (userId == null || assetSymbol == null || assetSymbol.isBlank()) {
      return;
    }
    String symbol = assetSymbol.trim().toUpperCase();
    List<DividendDetail> accrued =
        dividendDetailRepository.findAccruedByUserAndSymbol(userId, symbol);
    if (accrued.isEmpty()) {
      return;
    }
    List<Transaction> userHistory =
        transactionRepository.findByUserIdAndAssetSymbol(userId, symbol, Sort.unsorted());
    int removed = 0;
    int adjusted = 0;
    for (DividendDetail detail : accrued) {
      LocalDate exDate = detail.getExDividendDate();
      BigDecimal entitled =
          exDate == null
              ? BigDecimal.ZERO
              : entitlementCalculator
                  .entitledQuantityByUser(userHistory, exDate)
                  .getOrDefault(userId, BigDecimal.ZERO);
      if (entitled.signum() <= 0) {
        UUID dividendTxId = detail.getTransaction().getTransactionId();
        transactionRepository.deleteById(dividendTxId);
        idempotencyService.forgetByResultTransactionId(dividendTxId);
        removed++;
      } else if (detail.getAmountPerShare() != null) {
        DividendTaxBreakdown tax =
            taxCalculator.foreignCashDividend(entitled, detail.getAmountPerShare());
        Transaction tx = detail.getTransaction();
        if (tx.getTotalValue() == null || tx.getTotalValue().compareTo(tax.grossAmount()) != 0) {
          tx.setTotalValue(tax.grossAmount());
          transactionRepository.save(tx);
          detail.setTaxWithheld(tax.totalWithheld());
          dividendDetailRepository.save(detail);
          adjusted++;
        }
      }
    }
    if (removed > 0 || adjusted > 0) {
      log.info(
          "Reconciliación dividendos {} user={}: {} eliminados, {} ajustados",
          symbol,
          userId,
          removed,
          adjusted);
    }
  }
}
