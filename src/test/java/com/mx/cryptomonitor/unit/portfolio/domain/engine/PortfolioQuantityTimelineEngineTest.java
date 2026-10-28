package com.mx.cryptomonitor.unit.portfolio.domain.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.mx.cryptomonitor.portfolio.domain.engine.PortfolioQuantityTimelineEngine;
import com.mx.cryptomonitor.portfolio.domain.exception.InsufficientFundsException;
import com.mx.cryptomonitor.portfolio.domain.model.AssetType;
import com.mx.cryptomonitor.portfolio.domain.model.PortfolioAccountingTransaction;
import com.mx.cryptomonitor.portfolio.domain.model.QuantityTimelinePoint;

class PortfolioQuantityTimelineEngineTest {

  private final PortfolioQuantityTimelineEngine engine = new PortfolioQuantityTimelineEngine();

  @Test
  void returnsEmptyTimelineWhenThereAreNoTransactions() {
    assertThat(engine.buildQuantityTimeline(List.of())).isEmpty();
  }

  @Test
  void reconstructsSingleAndMultipleBuys() {
    List<QuantityTimelinePoint> timeline =
        engine.buildQuantityTimeline(
            List.of(
                tx("2026-01-01T00:00:00Z", "BUY", "1"), tx("2026-01-02T00:00:00Z", "BUY", "2")));

    assertThat(timeline)
        .extracting(QuantityTimelinePoint::quantity)
        .containsExactly(new BigDecimal("1"), new BigDecimal("3"));
  }

  @Test
  void handlesPartialSellAndSellAll() {
    List<QuantityTimelinePoint> timeline =
        engine.buildQuantityTimeline(
            List.of(
                tx("2026-01-01T00:00:00Z", "BUY", "3"),
                tx("2026-01-02T00:00:00Z", "SELL", "1.5"),
                tx("2026-01-03T00:00:00Z", "SELL", "1.5")));

    assertThat(timeline)
        .extracting(QuantityTimelinePoint::quantity)
        .containsExactly(new BigDecimal("3"), new BigDecimal("1.5"), new BigDecimal("0.0"));
  }

  @Test
  void sortsUnorderedInputByTime() {
    List<QuantityTimelinePoint> timeline =
        engine.buildQuantityTimeline(
            List.of(
                tx("2026-01-02T00:00:00Z", "SELL", "1"), tx("2026-01-01T00:00:00Z", "BUY", "2")));

    assertThat(timeline)
        .extracting(QuantityTimelinePoint::time)
        .containsExactly(
            Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-02T00:00:00Z"));
    assertThat(timeline)
        .extracting(QuantityTimelinePoint::quantity)
        .containsExactly(new BigDecimal("2"), new BigDecimal("1"));
  }

  @Test
  void sameTimestampTransactionsPreserveInsertionOrder() {
    Instant sameTime = Instant.parse("2026-01-01T00:00:00Z");

    List<QuantityTimelinePoint> timeline =
        engine.buildQuantityTimeline(
            List.of(tx(sameTime, "BUY", "2"), tx(sameTime, "SELL", "1"), tx(sameTime, "BUY", "3")));

    assertThat(timeline)
        .extracting(QuantityTimelinePoint::quantity)
        .containsExactly(new BigDecimal("2"), new BigDecimal("1"), new BigDecimal("4"));
  }

  @Test
  void sellMoreThanOwnedThrowsException() {
    assertThatThrownBy(
            () ->
                engine.buildQuantityTimeline(
                    List.of(
                        tx("2026-01-01T00:00:00Z", "BUY", "1"),
                        tx("2026-01-02T00:00:00Z", "SELL", "2"))))
        .isInstanceOf(InsufficientFundsException.class)
        .hasMessageContaining("Cannot sell more holdings than owned");
  }

  @Test
  void returnsDeterministicTimelineForRepeatedRuns() {
    List<PortfolioAccountingTransaction> transactions =
        List.of(
            tx("2026-01-01T00:00:00Z", "BUY", "2"),
            tx("2026-01-02T00:00:00Z", "BUY", "1"),
            tx("2026-01-03T00:00:00Z", "SELL", "0.5"));

    assertThat(engine.buildQuantityTimeline(transactions))
        .isEqualTo(engine.buildQuantityTimeline(transactions));
  }

  private PortfolioAccountingTransaction tx(String time, String type, String quantity) {
    return tx(Instant.parse(time), type, quantity);
  }

  private PortfolioAccountingTransaction tx(Instant time, String type, String quantity) {
    return new PortfolioAccountingTransaction(
        time,
        "SOL",
        AssetType.CRYPTO,
        type,
        new BigDecimal(quantity),
        new BigDecimal("100"),
        new BigDecimal("100"),
        BigDecimal.ZERO);
  }
}
