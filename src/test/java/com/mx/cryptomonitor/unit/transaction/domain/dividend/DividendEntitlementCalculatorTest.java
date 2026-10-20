package com.mx.cryptomonitor.unit.transaction.domain.dividend;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mx.cryptomonitor.transaction.domain.dividend.DividendEntitlementCalculator;
import com.mx.cryptomonitor.transaction.domain.model.Transaction;
import com.mx.cryptomonitor.user.domain.model.User;

class DividendEntitlementCalculatorTest {

  private final DividendEntitlementCalculator calculator = new DividendEntitlementCalculator();
  private static final LocalDate EX_DATE = LocalDate.of(2024, 6, 10);

  private final UUID userA = UUID.randomUUID();
  private final UUID userB = UUID.randomUUID();

  private Transaction tx(UUID userId, String type, String qty, LocalDate operationDate) {
    return Transaction.builder()
        .user(User.builder().id(userId).build())
        .assetSymbol("GOOGL")
        .transactionType(type)
        .quantity(new BigDecimal(qty))
        .localExecutionTime(operationDate.atStartOfDay())
        .build();
  }

  private Transaction transfer(UUID userId, String transferType, String qty, LocalDate date) {
    Transaction t = tx(userId, "TRANSFER", qty, date);
    t.setTransferType(transferType);
    return t;
  }

  @Test
  void sumsBuysMinusSellsBeforeExDate() {
    Map<UUID, BigDecimal> entitled =
        calculator.entitledQuantityByUser(
            List.of(
                tx(userA, "BUY", "10", LocalDate.of(2023, 10, 1)),
                tx(userA, "BUY", "5", LocalDate.of(2024, 1, 15)),
                tx(userA, "SELL", "3", LocalDate.of(2024, 3, 1))),
            EX_DATE);

    assertThat(entitled).containsOnlyKeys(userA);
    assertThat(entitled.get(userA)).isEqualByComparingTo("12");
  }

  @Test
  void excludesOperationsOnOrAfterExDate() {
    // Comprar el dia ex (o despues) NO da derecho: regla estricta <.
    Map<UUID, BigDecimal> entitled =
        calculator.entitledQuantityByUser(
            List.of(
                tx(userA, "BUY", "8", LocalDate.of(2024, 6, 9)),
                tx(userA, "BUY", "100", EX_DATE),
                tx(userA, "BUY", "50", LocalDate.of(2024, 6, 11))),
            EX_DATE);

    assertThat(entitled.get(userA)).isEqualByComparingTo("8");
  }

  @Test
  void handlesTransfersInAndOut() {
    Map<UUID, BigDecimal> entitled =
        calculator.entitledQuantityByUser(
            List.of(
                transfer(userA, "TRANSFER_IN", "20", LocalDate.of(2024, 1, 1)),
                transfer(userA, "TRANSFER_OUT", "5", LocalDate.of(2024, 2, 1))),
            EX_DATE);

    assertThat(entitled.get(userA)).isEqualByComparingTo("15");
  }

  @Test
  void separatesHoldingsPerUserAndDropsNonPositive() {
    Map<UUID, BigDecimal> entitled =
        calculator.entitledQuantityByUser(
            List.of(
                tx(userA, "BUY", "10", LocalDate.of(2024, 1, 1)),
                tx(userB, "BUY", "4", LocalDate.of(2024, 1, 1)),
                tx(userB, "SELL", "4", LocalDate.of(2024, 2, 1))),
            EX_DATE);

    assertThat(entitled).containsOnlyKeys(userA);
    assertThat(entitled.get(userA)).isEqualByComparingTo("10");
  }

  @Test
  void dividendRowsDoNotAffectHoldings() {
    Transaction dividend = tx(userA, "DIVIDEND", "0", LocalDate.of(2024, 1, 5));
    Map<UUID, BigDecimal> entitled =
        calculator.entitledQuantityByUser(
            List.of(tx(userA, "BUY", "7", LocalDate.of(2024, 1, 1)), dividend), EX_DATE);

    assertThat(entitled.get(userA)).isEqualByComparingTo("7");
  }

  @Test
  void emptyWhenNobodyHeldBeforeExDate() {
    Map<UUID, BigDecimal> entitled =
        calculator.entitledQuantityByUser(
            List.of(tx(userA, "BUY", "5", LocalDate.of(2024, 6, 20))), EX_DATE);

    assertThat(entitled).isEmpty();
  }
}
