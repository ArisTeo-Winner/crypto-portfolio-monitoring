package com.mx.cryptomonitor.transaction.domain.dividend;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mx.cryptomonitor.transaction.domain.model.Transaction;

/**
 * Resuelve el derecho a dividendo (ADR-0014 F3c): para una fecha ex-dividendo, cuanto tenia cada
 * usuario de un simbolo. Regla de entitlement (ADR-0013, {@code <} estricto): solo cuentan las
 * operaciones cuya fecha de operacion es <b>anterior</b> a la ex-date (comprar el dia ex ya no da
 * derecho). Cantidad firmada: BUY / TRANSFER_IN suman, SELL / TRANSFER_OUT restan; DIVIDEND y otros
 * no mueven la tenencia.
 *
 * <p>Solo devuelve usuarios con tenencia neta positiva en la ex-date.
 */
public final class DividendEntitlementCalculator {

  public Map<java.util.UUID, BigDecimal> entitledQuantityByUser(
      List<Transaction> transactions, LocalDate exDividendDate) {
    Map<java.util.UUID, BigDecimal> net = new LinkedHashMap<>();
    for (Transaction tx : transactions) {
      if (tx.getUser() == null || !isBeforeExDate(tx, exDividendDate)) {
        continue;
      }
      BigDecimal signed = signedQuantity(tx);
      if (signed.signum() == 0) {
        continue;
      }
      net.merge(tx.getUser().getId(), signed, BigDecimal::add);
    }
    net.values().removeIf(quantity -> quantity.signum() <= 0);
    return net;
  }

  private boolean isBeforeExDate(Transaction tx, LocalDate exDividendDate) {
    LocalDate operationDate = operationDate(tx);
    return operationDate != null && operationDate.isBefore(exDividendDate);
  }

  private LocalDate operationDate(Transaction tx) {
    if (tx.getLocalExecutionTime() != null) {
      return tx.getLocalExecutionTime().toLocalDate();
    }
    if (tx.getExecutedAtUtc() != null) {
      return tx.getExecutedAtUtc().toLocalDate();
    }
    return tx.getTransactionDate() != null ? tx.getTransactionDate().toLocalDate() : null;
  }

  private BigDecimal signedQuantity(Transaction tx) {
    BigDecimal quantity = tx.getQuantity() != null ? tx.getQuantity() : BigDecimal.ZERO;
    String type = tx.getTransactionType();
    if ("BUY".equalsIgnoreCase(type)) {
      return quantity;
    }
    if ("SELL".equalsIgnoreCase(type)) {
      return quantity.negate();
    }
    if ("TRANSFER".equalsIgnoreCase(type)) {
      return "TRANSFER_OUT".equalsIgnoreCase(tx.getTransferType()) ? quantity.negate() : quantity;
    }
    return BigDecimal.ZERO;
  }
}
