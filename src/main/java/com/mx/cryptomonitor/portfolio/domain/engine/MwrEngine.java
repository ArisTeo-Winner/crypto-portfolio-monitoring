package com.mx.cryptomonitor.portfolio.domain.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.mx.cryptomonitor.portfolio.domain.model.PortfolioAccountingTransaction;

/**
 * Computes the Money-Weighted Return (MWR), also known as the Internal Rate of Return (IRR) or
 * XIRR, for a portfolio.
 *
 * <p>Unlike TWR, the MWR reflects the actual investor experience: it accounts for both the size and
 * timing of cash flows. Large investments made just before a market drop lower the MWR relative to
 * the TWR. This mirrors how IBKR PortfolioAnalyst reports MWR.
 *
 * <p>Cash-flow convention (from the investor's perspective):
 *
 * <ul>
 *   <li>BUY: outflow → {@code -(grossValue + fee)} (money leaves the investor's pocket)
 *   <li>SELL: inflow → {@code +(grossValue − fee)} (money returns to the investor)
 *   <li>Terminal portfolio value: inflow at {@code terminalDate}
 * </ul>
 *
 * <p>The XIRR equation solved here is:
 *
 * <pre>
 *   NPV(r) = Σ CF_i / (1+r)^(d_i/365.25) = 0
 * </pre>
 *
 * where {@code d_i} is the number of days from the earliest cash-flow date.
 *
 * <p>Solution method: Newton-Raphson with up to {@value #MAX_ITERATIONS} iterations and an initial
 * guess of 10 %. Returns {@code null} when there is insufficient data or when the solver does not
 * converge (e.g. all cash flows have the same sign or the same date).
 */
public class MwrEngine {

  private static final int RETURN_SCALE = 6;
  private static final int MAX_ITERATIONS = 200;
  private static final double TOLERANCE = 1e-7;
  private static final double SECONDS_PER_YEAR = 365.25 * 86_400.0;
  private static final double INITIAL_GUESS = 0.10;
  private static final double RATE_MIN = -0.9999;
  private static final double RATE_MAX = 1_000.0;

  /**
   * Computes the MWR.
   *
   * @param transactions all portfolio transactions (BUY / SELL)
   * @param terminalValue current market value of the portfolio at {@code terminalDate}
   * @param terminalDate the date/time used as the final cash-flow event
   * @return MWR as a decimal fraction, or {@code null} when the solver cannot converge
   */
  public BigDecimal calculate(
      List<PortfolioAccountingTransaction> transactions,
      BigDecimal terminalValue,
      Instant terminalDate) {
    if (transactions == null
        || transactions.isEmpty()
        || terminalValue == null
        || terminalDate == null) {
      return null;
    }

    List<CashFlow> cashFlows = buildCashFlows(transactions, terminalValue, terminalDate);
    if (cashFlows.size() < 2) {
      return null;
    }

    long t0 = cashFlows.stream().mapToLong(CashFlow::epochSecond).min().orElse(0L);

    // Solver cannot work if all cash flows occur on the same day
    boolean allSameTime = cashFlows.stream().allMatch(cf -> cf.epochSecond() == t0);
    if (allSameTime) {
      return null;
    }

    double rate = solveNewtonRaphson(cashFlows, t0);

    if (!Double.isFinite(rate)) {
      return null;
    }

    return BigDecimal.valueOf(rate).setScale(RETURN_SCALE, RoundingMode.HALF_UP);
  }

  // ── helpers ──────────────────────────────────────────────────────────────────

  private List<CashFlow> buildCashFlows(
      List<PortfolioAccountingTransaction> transactions,
      BigDecimal terminalValue,
      Instant terminalDate) {
    List<CashFlow> flows = new ArrayList<>(transactions.size() + 1);

    for (PortfolioAccountingTransaction tx : transactions) {
      long t = tx.time().getEpochSecond();
      double gross = safeDouble(tx.grossValue());
      double fee = safeDouble(tx.fee());

      if ("BUY".equalsIgnoreCase(tx.transactionType())) {
        flows.add(new CashFlow(t, -(gross + fee)));
      } else if ("SELL".equalsIgnoreCase(tx.transactionType())) {
        flows.add(new CashFlow(t, gross - fee));
      }
    }

    flows.add(new CashFlow(terminalDate.getEpochSecond(), terminalValue.doubleValue()));
    return flows;
  }

  private double solveNewtonRaphson(List<CashFlow> cashFlows, long t0) {
    double rate = INITIAL_GUESS;

    for (int i = 0; i < MAX_ITERATIONS; i++) {
      double npv = npv(cashFlows, t0, rate);
      double dnpv = dnpv(cashFlows, t0, rate);

      if (Math.abs(dnpv) < 1e-14) {
        break; // Derivative is essentially zero — stop
      }

      double delta = npv / dnpv;
      rate -= delta;

      // Clamp to a sensible range to prevent wild divergence
      rate = Math.max(RATE_MIN, Math.min(RATE_MAX, rate));

      if (Math.abs(delta) < TOLERANCE) {
        return rate;
      }
    }

    // Return best estimate if |NPV| is small enough, otherwise signal failure
    return Math.abs(npv(cashFlows, t0, rate)) < 1.0 ? rate : Double.NaN;
  }

  /** NPV of all cash flows discounted at {@code rate} relative to {@code t0}. */
  private double npv(List<CashFlow> cashFlows, long t0, double rate) {
    double sum = 0.0;
    for (CashFlow cf : cashFlows) {
      double years = (cf.epochSecond() - t0) / SECONDS_PER_YEAR;
      sum += cf.amount() / Math.pow(1.0 + rate, years);
    }
    return sum;
  }

  /** First derivative of NPV with respect to {@code rate}. */
  private double dnpv(List<CashFlow> cashFlows, long t0, double rate) {
    double sum = 0.0;
    for (CashFlow cf : cashFlows) {
      double years = (cf.epochSecond() - t0) / SECONDS_PER_YEAR;
      sum -= cf.amount() * years / Math.pow(1.0 + rate, years + 1.0);
    }
    return sum;
  }

  private double safeDouble(BigDecimal value) {
    return value != null ? value.doubleValue() : 0.0;
  }

  private record CashFlow(long epochSecond, double amount) {}
}
