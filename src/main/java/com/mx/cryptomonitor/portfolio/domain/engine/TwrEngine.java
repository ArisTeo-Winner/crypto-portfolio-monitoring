package com.mx.cryptomonitor.portfolio.domain.engine;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.mx.cryptomonitor.portfolio.domain.model.PortfolioAccountingTransaction;
import com.mx.cryptomonitor.portfolio.domain.model.TimeValuePoint;

/**
 * Computes the Time-Weighted Return (TWR) for a portfolio.
 *
 * <p>The TWR chains sub-period holding-period returns (HPR) at each external cash-flow event
 * (BUY / SELL transaction), which eliminates the distortion caused by the timing and size of
 * investor deposits and withdrawals. This is the same methodology used by IBKR PortfolioAnalyst.
 *
 * <p>Algorithm:
 * <ol>
 *   <li>Sort all unique transaction timestamps that fall strictly within the value-series range.
 *   <li>For each consecutive pair of boundaries {@code [t_prev, t_curr]}:
 *       <ul>
 *         <li>{@code HPR = lastValueBefore(t_curr) / firstValueAtOrAfter(t_prev)}
 *       </ul>
 *   <li>{@code TWR = ∏ HPR_i − 1}
 * </ol>
 *
 * <p>If either boundary value is zero or unavailable the sub-period HPR defaults to {@code 1}
 * (neutral), avoiding division by zero while preserving the chain.
 */
public class TwrEngine {

  private static final int RETURN_SCALE = 6;

  /**
   * Calculates the TWR for the given portfolio value series and list of transactions.
   *
   * @param series aggregated portfolio value at each point in time (may be unsorted)
   * @param transactions all accounting transactions used to demarcate sub-periods
   * @return TWR as a decimal fraction, or {@link BigDecimal#ZERO} when there is insufficient data
   */
  public BigDecimal calculate(
      List<TimeValuePoint> series, List<PortfolioAccountingTransaction> transactions) {
    if (series == null || series.size() < 2) {
      return BigDecimal.ZERO;
    }

    List<TimeValuePoint> sorted =
        series.stream().sorted(Comparator.comparingLong(TimeValuePoint::time)).toList();

    long seriesStart = sorted.get(0).time();
    long seriesEnd = sorted.get(sorted.size() - 1).time();

    // Transaction times that act as sub-period boundaries (strictly inside the series range)
    List<Long> boundaries =
        (transactions == null ? List.<PortfolioAccountingTransaction>of() : transactions).stream()
            .map(tx -> tx.time().getEpochSecond())
            .filter(t -> t > seriesStart && t < seriesEnd)
            .distinct()
            .sorted()
            .toList();

    if (boundaries.isEmpty()) {
      // No cash-flow events: single sub-period covering the entire series
      return singlePeriodReturn(sorted);
    }

    BigDecimal cumulativeReturn = BigDecimal.ONE;
    long periodStart = seriesStart;

    for (Long boundary : boundaries) {
      BigDecimal startValue = firstValueAtOrAfter(sorted, periodStart);
      BigDecimal endValue = lastValueBefore(sorted, boundary);
      cumulativeReturn =
          cumulativeReturn.multiply(hpr(startValue, endValue), MathContext.DECIMAL128);
      periodStart = boundary;
    }

    // Final sub-period: from last boundary (inclusive) to series end
    BigDecimal startValue = firstValueAtOrAfter(sorted, periodStart);
    BigDecimal endValue = sorted.get(sorted.size() - 1).value();
    cumulativeReturn = cumulativeReturn.multiply(hpr(startValue, endValue), MathContext.DECIMAL128);

    return cumulativeReturn.subtract(BigDecimal.ONE).setScale(RETURN_SCALE, RoundingMode.HALF_UP);
  }

  // ── helpers ──────────────────────────────────────────────────────────────────

  private BigDecimal singlePeriodReturn(List<TimeValuePoint> sorted) {
    BigDecimal first = sorted.get(0).value();
    BigDecimal last = sorted.get(sorted.size() - 1).value();
    return hpr(first, last).subtract(BigDecimal.ONE).setScale(RETURN_SCALE, RoundingMode.HALF_UP);
  }

  /**
   * Holding-period return as a factor (not a percentage). Returns {@link BigDecimal#ONE} when
   * either bound is non-positive or null so it does not distort the chain.
   */
  private BigDecimal hpr(BigDecimal startValue, BigDecimal endValue) {
    if (startValue == null || startValue.compareTo(BigDecimal.ZERO) <= 0) {
      return BigDecimal.ONE;
    }
    if (endValue == null || endValue.compareTo(BigDecimal.ZERO) < 0) {
      return BigDecimal.ONE;
    }
    return endValue.divide(startValue, MathContext.DECIMAL128);
  }

  /** Last series point whose timestamp is strictly less than {@code epochSecond}. */
  private BigDecimal lastValueBefore(List<TimeValuePoint> sorted, long epochSecond) {
    BigDecimal last = null;
    for (TimeValuePoint point : sorted) {
      if (point.time() < epochSecond) {
        last = point.value();
      } else {
        break;
      }
    }
    return last;
  }

  /** First series point whose timestamp is greater than or equal to {@code epochSecond}. */
  private BigDecimal firstValueAtOrAfter(List<TimeValuePoint> sorted, long epochSecond) {
    List<TimeValuePoint> candidates = new ArrayList<>();
    for (TimeValuePoint point : sorted) {
      if (point.time() >= epochSecond) {
        candidates.add(point);
        break;
      }
    }
    return candidates.isEmpty() ? null : candidates.get(0).value();
  }
}
