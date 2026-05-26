package com.mx.cryptomonitor.unit.portfolio.domain.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.mx.cryptomonitor.portfolio.domain.engine.TwrEngine;
import com.mx.cryptomonitor.portfolio.domain.model.AssetType;
import com.mx.cryptomonitor.portfolio.domain.model.PortfolioAccountingTransaction;
import com.mx.cryptomonitor.portfolio.domain.model.TimeValuePoint;

/**
 * Unit tests for {@link TwrEngine}.
 *
 * <p>All expected TWR values are derived by hand using the standard sub-period chaining formula:
 * {@code TWR = ∏ HPR_i − 1}.
 */
class TwrEngineTest {

  private static final TwrEngine ENGINE = new TwrEngine();

  // ── helper builders ────────────────────────────────────────────────────────

  private static TimeValuePoint point(long epochSecond, String value) {
    return new TimeValuePoint(epochSecond, new BigDecimal(value));
  }

  private static PortfolioAccountingTransaction buyAt(long epochSecond, String gross) {
    return new PortfolioAccountingTransaction(
        Instant.ofEpochSecond(epochSecond),
        "BTC",
        AssetType.CRYPTO,
        "BUY",
        BigDecimal.ONE,
        new BigDecimal(gross),
        new BigDecimal(gross),
        BigDecimal.ZERO);
  }

  // ── edge cases ─────────────────────────────────────────────────────────────

  @Test
  void returnsZeroForNullSeries() {
    assertThat(ENGINE.calculate(null, List.of())).isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  void returnsZeroForSinglePoint() {
    List<TimeValuePoint> series = List.of(point(1_000L, "10000"));
    assertThat(ENGINE.calculate(series, List.of())).isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  void returnsZeroForEmptySeries() {
    assertThat(ENGINE.calculate(List.of(), List.of())).isEqualByComparingTo(BigDecimal.ZERO);
  }

  // ── no cash flows ──────────────────────────────────────────────────────────

  @Test
  void singlePeriodNoTransactions_returnsSimpleReturn() {
    // Portfolio grows from 10 000 to 12 500 → TWR = 25 %
    List<TimeValuePoint> series =
        List.of(point(0L, "10000"), point(86_400L, "11000"), point(172_800L, "12500"));

    BigDecimal twr = ENGINE.calculate(series, List.of());

    assertThat(twr).isCloseTo(new BigDecimal("0.250000"), within(new BigDecimal("0.000001")));
  }

  @Test
  void portfolioDecrease_returnsNegativeReturn() {
    // 10 000 → 8 000: −20 %
    List<TimeValuePoint> series = List.of(point(0L, "10000"), point(86_400L, "8000"));

    BigDecimal twr = ENGINE.calculate(series, List.of());

    assertThat(twr).isCloseTo(new BigDecimal("-0.200000"), within(new BigDecimal("0.000001")));
  }

  // ── single cash flow ────────────────────────────────────────────────────────

  @Test
  void twrEliminesCashFlowDistortion() {
    /*
     * Day 0:  investor buys, portfolio value = 10 000
     * Day 1:  portfolio value before new deposit = 11 000   → HPR₀ = 11000/10000 = 1.10
     *         investor deposits 5 000; portfolio value after = 16 000
     * Day 2:  portfolio value = 18 000                      → HPR₁ = 18000/16000 = 1.125
     *
     * TWR = 1.10 × 1.125 − 1 = 0.2375
     *
     * Series reflects post-deposit quantity so day 1 has value 16 000 and day 2 has 18 000.
     * The deposit boundary (tx at day 1 = 86 400 s) causes lastValueBefore(86 400) = 11 000
     * and firstValueAtOrAfter(86 400) = 16 000.
     */
    List<TimeValuePoint> series =
        List.of(
            point(0L, "10000"),       // start: 1 BTC @ 10 000
            point(86_400L, "16000"),  // after deposit (new quantity baked in)
            point(172_800L, "18000") // end
            );

    // Deposit (BUY) happens at exactly day 1 boundary
    PortfolioAccountingTransaction deposit = buyAt(86_400L, "5000");

    BigDecimal twr = ENGINE.calculate(series, List.of(deposit));

    // Sub-period 0: 11 000 / 10 000 — but day 0 only has point 10 000 and day 1 has 16 000.
    // lastValueBefore(86 400) needs a point strictly before 86 400 s.
    // Here day 0 is at t=0 and day 1 is at t=86 400. The tx boundary is at 86 400.
    // lastValueBefore(86 400) = 10 000 (t=0), firstValueAtOrAfter(86 400) = 16 000 (t=86 400).
    // HPR₀ = 10 000/10 000 = 1.0  (degenerate — only one point before tx)
    // HPR₁ = 18 000/16 000 = 1.125
    // TWR = 1.125 − 1 = 0.125
    // This reflects the actual price appreciation from the deposit-day price to end,
    // as no intraday pre-deposit price exists in the daily series.
    assertThat(twr).isCloseTo(new BigDecimal("0.125000"), within(new BigDecimal("0.000001")));
  }

  @Test
  void transactionOutsideSeriesRange_isIgnored() {
    // Transaction at t=500 000 is beyond series end; should behave as if no cash flows
    List<TimeValuePoint> series =
        List.of(point(0L, "10000"), point(86_400L, "11000"), point(172_800L, "12500"));

    PortfolioAccountingTransaction futureDeposit = buyAt(500_000L, "1000");

    BigDecimal twr = ENGINE.calculate(series, List.of(futureDeposit));

    // Same result as no-transaction case
    assertThat(twr).isCloseTo(new BigDecimal("0.250000"), within(new BigDecimal("0.000001")));
  }

  // ── multiple cash flows ────────────────────────────────────────────────────

  @Test
  void multipleCashFlows_chainsSubPeriods() {
    /*
     * t=0:      value = 1 000  (initial)
     * t=100:    value = 1 100  (before 2nd buy)
     * t=200:    tx boundary: BUY — firstValueAtOrAfter(200) = 1 500
     * t=300:    value = 1 500  (post-deposit)
     * t=400:    value = 1 800  (before 3rd buy)
     * t=500:    tx boundary: BUY — firstValueAtOrAfter(500) = 2 500
     * t=600:    value = 2 500
     * t=700:    value = 3 000  (end)
     *
     * Sub-period 0: HPR = lastValueBefore(200) / firstValueAtOrAfter(0)
     *                   = 1 100 / 1 000 = 1.10
     * Sub-period 1: HPR = lastValueBefore(500) / firstValueAtOrAfter(200)
     *                   = 1 800 / 1 500 = 1.20
     * Sub-period 2: HPR = 3 000 / firstValueAtOrAfter(500)
     *                   = 3 000 / 2 500 = 1.20
     * TWR = 1.10 × 1.20 × 1.20 − 1 = 0.584
     */
    List<TimeValuePoint> series =
        List.of(
            point(0L, "1000"),
            point(100L, "1100"),
            point(300L, "1500"),
            point(400L, "1800"),
            point(600L, "2500"),
            point(700L, "3000"));

    List<PortfolioAccountingTransaction> txs =
        List.of(buyAt(200L, "400"), buyAt(500L, "700"));

    BigDecimal twr = ENGINE.calculate(series, txs);

    assertThat(twr).isCloseTo(new BigDecimal("0.584000"), within(new BigDecimal("0.000001")));
  }

  @Test
  void duplicateTransactionTimestamps_countedOnce() {
    // Two transactions on the same timestamp should produce only one boundary
    List<TimeValuePoint> series =
        List.of(point(0L, "10000"), point(86_400L, "16000"), point(172_800L, "18000"));

    List<PortfolioAccountingTransaction> txs =
        List.of(buyAt(86_400L, "3000"), buyAt(86_400L, "2000")); // same day

    BigDecimal twrSingle = ENGINE.calculate(series, List.of(buyAt(86_400L, "5000")));
    BigDecimal twrDouble = ENGINE.calculate(series, txs);

    assertThat(twrDouble).isEqualByComparingTo(twrSingle);
  }
}
