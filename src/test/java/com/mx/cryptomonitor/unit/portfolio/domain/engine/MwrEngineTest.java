package com.mx.cryptomonitor.unit.portfolio.domain.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.mx.cryptomonitor.portfolio.domain.engine.MwrEngine;
import com.mx.cryptomonitor.portfolio.domain.model.AssetType;
import com.mx.cryptomonitor.portfolio.domain.model.PortfolioAccountingTransaction;

/**
 * Unit tests for {@link MwrEngine}.
 *
 * <p>Expected MWR values are verified by substituting back into the XIRR equation
 * ({@code NPV ≈ 0}) or computed independently with Excel XIRR for cross-validation.
 */
class MwrEngineTest {

  private static final MwrEngine ENGINE = new MwrEngine();

  // ── helper builders ────────────────────────────────────────────────────────

  private static final Instant BASE = Instant.parse("2024-01-01T00:00:00Z");

  private static Instant daysAfterBase(int days) {
    return BASE.plusSeconds((long) days * 86_400);
  }

  private static PortfolioAccountingTransaction buy(
      int daysAfterBase, String gross, String fee) {
    BigDecimal g = new BigDecimal(gross);
    BigDecimal f = new BigDecimal(fee);
    return new PortfolioAccountingTransaction(
        daysAfterBase(daysAfterBase),
        "BTC",
        AssetType.CRYPTO,
        "BUY",
        BigDecimal.ONE,
        g,
        g,
        f);
  }

  private static PortfolioAccountingTransaction sell(
      int daysAfterBase, String gross, String fee) {
    BigDecimal g = new BigDecimal(gross);
    BigDecimal f = new BigDecimal(fee);
    return new PortfolioAccountingTransaction(
        daysAfterBase(daysAfterBase),
        "BTC",
        AssetType.CRYPTO,
        "SELL",
        BigDecimal.ONE,
        g,
        g,
        f);
  }

  // ── null / empty guards ────────────────────────────────────────────────────

  @Test
  void returnsNullForNullTransactions() {
    assertThat(ENGINE.calculate(null, BigDecimal.valueOf(10_000), BASE)).isNull();
  }

  @Test
  void returnsNullForEmptyTransactions() {
    assertThat(ENGINE.calculate(List.of(), BigDecimal.valueOf(10_000), BASE)).isNull();
  }

  @Test
  void returnsNullForNullTerminalValue() {
    assertThat(ENGINE.calculate(List.of(buy(0, "10000", "0")), null, BASE)).isNull();
  }

  @Test
  void returnsNullForNullTerminalDate() {
    assertThat(ENGINE.calculate(List.of(buy(0, "10000", "0")), BigDecimal.valueOf(10_000), null))
        .isNull();
  }

  @Test
  void returnsNullWhenAllCashFlowsOnSameDay() {
    // If all events happen at the same instant the solver cannot compute a rate
    List<PortfolioAccountingTransaction> txs = List.of(buy(0, "10000", "0"));
    BigDecimal terminalValue = BigDecimal.valueOf(12_000);
    Instant terminalDate = daysAfterBase(0); // same day as BUY

    assertThat(ENGINE.calculate(txs, terminalValue, terminalDate)).isNull();
  }

  // ── simple one-year scenario ───────────────────────────────────────────────

  @Test
  void exactlyOneYearTenPercentGain_mwrApproximatelyTenPercent() {
    /*
     * Buy 1 BTC at 10 000 on day 0, no fees.
     * Terminal value after exactly one year = 11 000.
     *
     * XIRR cash flows:
     *   Day   0: −10 000
     *   Day 365: +11 000  (terminal)
     *
     * XIRR solution: −10 000 + 11 000 / (1+r)^1 = 0  →  r = 0.10 exactly.
     */
    List<PortfolioAccountingTransaction> txs = List.of(buy(0, "10000", "0"));
    BigDecimal terminalValue = BigDecimal.valueOf(11_000);
    Instant terminalDate = daysAfterBase(365);

    BigDecimal mwr = ENGINE.calculate(txs, terminalValue, terminalDate);

    assertThat(mwr).isNotNull();
    assertThat(mwr).isCloseTo(new BigDecimal("0.100000"), within(new BigDecimal("0.0001")));
  }

  @Test
  void twentyPercentLoss_returnsNegativeMwr() {
    /*
     * Buy at 10 000, terminal value after 1 year = 8 000 → r = −0.20.
     */
    List<PortfolioAccountingTransaction> txs = List.of(buy(0, "10000", "0"));
    BigDecimal terminalValue = BigDecimal.valueOf(8_000);
    Instant terminalDate = daysAfterBase(365);

    BigDecimal mwr = ENGINE.calculate(txs, terminalValue, terminalDate);

    assertThat(mwr).isNotNull();
    assertThat(mwr).isCloseTo(new BigDecimal("-0.200000"), within(new BigDecimal("0.001")));
  }

  // ── fees included ─────────────────────────────────────────────────────────

  @Test
  void feesReduceEffectiveReturn() {
    /*
     * Buy at gross=10 000, fee=100 → investor spends 10 100.
     * Terminal value = 11 000 after 1 year.
     * Effective return < 10 % because cost basis is 10 100.
     */
    List<PortfolioAccountingTransaction> txs = List.of(buy(0, "10000", "100"));
    BigDecimal terminalValue = BigDecimal.valueOf(11_000);
    Instant terminalDate = daysAfterBase(365);

    BigDecimal mwrWithFee = ENGINE.calculate(txs, terminalValue, terminalDate);
    BigDecimal mwrNoFee =
        ENGINE.calculate(List.of(buy(0, "10000", "0")), terminalValue, terminalDate);

    assertThat(mwrWithFee).isNotNull();
    assertThat(mwrWithFee).isLessThan(mwrNoFee);
  }

  // ── multiple cash flows ────────────────────────────────────────────────────

  @Test
  void partialSell_reducesOutstandingInvestment() {
    /*
     * Day   0: BUY  2 BTC at 10 000 each → outflow 20 000
     * Day 180: SELL 1 BTC at 12 000      → inflow  12 000
     * Day 365: terminal value = 13 000   → inflow  13 000
     *
     * Net cash flows verified against NPV ≈ 0 at the computed rate.
     */
    List<PortfolioAccountingTransaction> txs =
        List.of(buy(0, "20000", "0"), sell(180, "12000", "0"));
    BigDecimal terminalValue = BigDecimal.valueOf(13_000);
    Instant terminalDate = daysAfterBase(365);

    BigDecimal mwr = ENGINE.calculate(txs, terminalValue, terminalDate);

    assertThat(mwr).isNotNull();
    // NPV should be approximately 0 at the computed rate
    assertThat(npvAt(mwr.doubleValue(), txs, terminalValue, terminalDate))
        .isCloseTo(0.0, within(1.0));
  }

  @Test
  void shortHoldingPeriod_highAnnualisedReturn() {
    /*
     * Buy at 10 000, terminal value 11 000 after 30 days.
     * XIRR: -10 000 + 11 000/(1+r)^(30/365.25) = 0
     *   → (1+r)^(30/365.25) = 1.10
     *   → r = 1.10^(365.25/30) − 1 ≈ 1.10^12.175 − 1 ≈ 2.19  (219 %)
     */
    List<PortfolioAccountingTransaction> txs = List.of(buy(0, "10000", "0"));
    BigDecimal terminalValue = BigDecimal.valueOf(11_000);
    Instant terminalDate = daysAfterBase(30);

    BigDecimal mwr = ENGINE.calculate(txs, terminalValue, terminalDate);

    assertThat(mwr).isNotNull();
    // Annualised return for a 10 % gain in 30 days is much larger than 100 %
    assertThat(mwr).isGreaterThan(new BigDecimal("2.0"));
  }

  // ── convergence verification helper ───────────────────────────────────────

  /**
   * Verifies that NPV ≈ 0 at the given rate — used to cross-check solver convergence without
   * hard-coding expected values for complex multi-flow scenarios.
   */
  private double npvAt(
      double rate,
      List<PortfolioAccountingTransaction> txs,
      BigDecimal terminalValue,
      Instant terminalDate) {
    long t0 =
        txs.stream()
            .mapToLong(tx -> tx.time().getEpochSecond())
            .min()
            .orElse(terminalDate.getEpochSecond());

    double secondsPerYear = 365.25 * 86_400.0;
    double npv = 0.0;

    for (PortfolioAccountingTransaction tx : txs) {
      double years = (tx.time().getEpochSecond() - t0) / secondsPerYear;
      double gross = tx.grossValue() != null ? tx.grossValue().doubleValue() : 0.0;
      double fee = tx.fee() != null ? tx.fee().doubleValue() : 0.0;
      double cf = "BUY".equalsIgnoreCase(tx.transactionType()) ? -(gross + fee) : (gross - fee);
      npv += cf / Math.pow(1 + rate, years);
    }

    double terminalYears = (terminalDate.getEpochSecond() - t0) / secondsPerYear;
    npv += terminalValue.doubleValue() / Math.pow(1 + rate, terminalYears);
    return npv;
  }
}
