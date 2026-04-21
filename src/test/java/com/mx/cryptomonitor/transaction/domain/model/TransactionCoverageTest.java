package com.mx.cryptomonitor.transaction.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import com.mx.cryptomonitor.user.domain.model.User;

class TransactionCoverageTest {

  @Test
  void jsonCreatorConstructorShouldHandleNullAndExplicitDate() {
    LocalDateTime explicitDate = LocalDateTime.of(2025, 1, 1, 9, 30);

    Transaction withExplicitDate = new Transaction(explicitDate);
    assertThat(withExplicitDate.getTransactionDate()).isEqualTo(explicitDate);

    Transaction withNullDate = new Transaction((LocalDateTime) null);
    assertThat(withNullDate.getTransactionDate()).isNotNull();
  }

  @Test
  void setFeeShouldAssignValue() {
    Transaction transaction = createBaseTransaction();
    BigDecimal expectedFee = new BigDecimal("12.50");

    transaction.setFee(expectedFee);

    assertThat(transaction.getFee()).isEqualByComparingTo(expectedFee);
  }

  @Test
  void equalsHashCodeAndCanEqualShouldCoverMainBranches() {
    Transaction base = createBaseTransaction();
    Transaction same = copyTransaction(base);

    assertThat(base).isEqualTo(same);
    assertThat(base.hashCode()).isEqualTo(same.hashCode());
    assertThat(base).isEqualTo(base);
    assertThat(base).isNotEqualTo(null);
    assertThat(base).isNotEqualTo(new Object());
    assertThat(base.toString()).contains("transactionId=").contains("assetSymbol=BTC");

    assertDifferentByMutation(
        base,
        TransactionCoverageTest::copyTransaction,
        List.of(
            t -> t.setTransactionId(UUID.randomUUID()),
            t -> t.setUser(createUser("u2", "u2@example.com")),
            t -> t.setPortfolioEntryId(UUID.randomUUID()),
            t -> t.setAssetSymbol("ETH"),
            t -> t.setAssetType(AssetType.STOCK),
            t -> t.setTransactionType("SELL"),
            t -> t.setQuantity(new BigDecimal("9.00000000")),
            t -> t.setPricePerUnit(new BigDecimal("90000.00000000")),
            t -> t.setTotalValue(new BigDecimal("810000.00")),
            t -> t.setTransactionDate(LocalDateTime.of(2025, 1, 2, 10, 0)),
            t -> t.setFee(new BigDecimal("1.00")),
            t -> t.setNotes("different notes"),
            t -> t.setCreatedAt(LocalDateTime.of(2025, 1, 3, 10, 0)),
            t -> t.setUpdatedAt(LocalDateTime.of(2025, 1, 4, 10, 0))));

    assertDifferentByMutation(
        base,
        TransactionCoverageTest::copyTransaction,
        List.of(
            t -> t.setTransactionId(null),
            t -> t.setUser(null),
            t -> t.setPortfolioEntryId(null),
            t -> t.setAssetSymbol(null),
            t -> t.setAssetType(null),
            t -> t.setTransactionType(null),
            t -> t.setQuantity(null),
            t -> t.setPricePerUnit(null),
            t -> t.setTotalValue(null),
            t -> t.setTransactionDate(null),
            t -> t.setFee(null),
            t -> t.setNotes(null),
            t -> t.setCreatedAt(null),
            t -> t.setUpdatedAt(null)));

    NonEqualTransaction incompatible = new NonEqualTransaction();
    copyValues(base, incompatible);
    assertThat(base.equals(incompatible)).isFalse();
  }

  @Test
  void equalsAndHashCodeShouldCoverNullToNonNullBranches() {
    Transaction allNullBase = new Transaction();
    allNullBase.setTransactionDate(null);
    allNullBase.setFee(null);
    allNullBase.setCreatedAt(null);
    allNullBase.setUpdatedAt(null);

    Transaction allNullCopy = copyTransaction(allNullBase);
    assertThat(allNullBase).isEqualTo(allNullCopy);
    assertThat(allNullBase.hashCode()).isEqualTo(allNullCopy.hashCode());

    assertDifferentByMutation(
        allNullBase,
        TransactionCoverageTest::copyTransaction,
        List.of(
            t -> t.setTransactionId(UUID.randomUUID()),
            t -> t.setUser(createUser("null-user", "null-user@example.com")),
            t -> t.setPortfolioEntryId(UUID.randomUUID()),
            t -> t.setAssetSymbol("BTC"),
            t -> t.setAssetType(AssetType.STOCK),
            t -> t.setTransactionType("BUY"),
            t -> t.setQuantity(BigDecimal.ONE),
            t -> t.setPricePerUnit(BigDecimal.TEN),
            t -> t.setTotalValue(BigDecimal.TEN),
            t -> t.setTransactionDate(LocalDateTime.of(2025, 2, 1, 10, 0)),
            t -> t.setFee(BigDecimal.ONE),
            t -> t.setNotes("note"),
            t -> t.setCreatedAt(LocalDateTime.of(2025, 2, 1, 10, 1)),
            t -> t.setUpdatedAt(LocalDateTime.of(2025, 2, 1, 10, 2))));
  }

  private static void assertDifferentByMutation(
      Transaction base,
      Function<Transaction, Transaction> copier,
      List<Consumer<Transaction>> mutators) {
    for (Consumer<Transaction> mutator : mutators) {
      Transaction variant = copier.apply(base);
      mutator.accept(variant);
      assertThat(base).isNotEqualTo(variant);
    }
  }

  private static Transaction createBaseTransaction() {
    User user = createUser("trx-user", "trx-user@example.com");

    return Transaction.builder()
        .transactionId(UUID.fromString("00000000-0000-0000-0000-000000000701"))
        .user(user)
        .portfolioEntryId(UUID.fromString("00000000-0000-0000-0000-000000000801"))
        .assetSymbol("BTC")
        .assetType(AssetType.CRYPTO)
        .transactionType("BUY")
        .quantity(new BigDecimal("1.00000000"))
        .pricePerUnit(new BigDecimal("50000.00000000"))
        .totalValue(new BigDecimal("50000.00"))
        .transactionDate(LocalDateTime.of(2025, 1, 1, 10, 0))
        .fee(new BigDecimal("0.50"))
        .notes("initial transaction")
        .createdAt(LocalDateTime.of(2025, 1, 1, 10, 1))
        .updatedAt(LocalDateTime.of(2025, 1, 1, 10, 2))
        .build();
  }

  private static Transaction copyTransaction(Transaction original) {
    Transaction copy = new Transaction();
    copyValues(original, copy);
    return copy;
  }

  private static void copyValues(Transaction source, Transaction target) {
    target.setTransactionId(source.getTransactionId());
    target.setUser(source.getUser());
    target.setPortfolioEntryId(source.getPortfolioEntryId());
    target.setAssetSymbol(source.getAssetSymbol());
    target.setAssetType(source.getAssetType());
    target.setTransactionType(source.getTransactionType());
    target.setQuantity(source.getQuantity());
    target.setPricePerUnit(source.getPricePerUnit());
    target.setTotalValue(source.getTotalValue());
    target.setTransactionDate(source.getTransactionDate());
    target.setFee(source.getFee());
    target.setNotes(source.getNotes());
    target.setCreatedAt(source.getCreatedAt());
    target.setUpdatedAt(source.getUpdatedAt());
  }

  private static User createUser(String username, String email) {
    User user = new User();
    user.setId(UUID.randomUUID());
    user.setUsername(username);
    user.setEmail(email);
    return user;
  }

  private static class NonEqualTransaction extends Transaction {
    @Override
    public boolean canEqual(Object other) {
      return false;
    }
  }
}
