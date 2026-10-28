package com.mx.cryptomonitor.marketdata.domain.dividend;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Calcula el crecimiento del dividendo de una seguridad (ADR-0014 F5). Agrega el DPS por año (solo
 * anios COMPLETOS: se excluye el anio en curso) y deriva la CAGR por horizonte:
 *
 * <pre>CAGR(N) = (DPS[ultimoAnio] / DPS[ultimoAnio - N]) ^ (1/N) - 1</pre>
 *
 * <p>Usa {@code split_adjusted_dps} (comparable entre splits) y solo dividendos recurring (el
 * filtro de recurring/split-adjusted lo aplica quien arma la lista de entrada). Reproduce el metodo
 * de Portfolio Dividend Tracker (suma por anio completo, CAGR punta a punta).
 */
public final class DividendGrowthCalculator {

  private static final int CAGR_SCALE = 6;
  private static final int[] HORIZONS = {1, 3, 5, 10};

  /** Un dividendo con su fecha ex (define el anio) y el DPS a sumar. */
  public record DatedDps(LocalDate exDividendDate, BigDecimal dps) {}

  public DividendGrowthResult compute(String symbol, List<DatedDps> dividends, int currentYear) {
    Map<Integer, BigDecimal> byYear = new TreeMap<>();
    for (DatedDps dividend : dividends) {
      if (dividend.exDividendDate() == null || dividend.dps() == null) {
        continue;
      }
      int year = dividend.exDividendDate().getYear();
      if (year >= currentYear) {
        continue; // anio en curso: incompleto
      }
      byYear.merge(year, dividend.dps(), BigDecimal::add);
    }
    if (byYear.isEmpty()) {
      return DividendGrowthResult.empty(symbol);
    }

    List<DividendGrowthResult.AnnualDividend> annual = new ArrayList<>();
    byYear.forEach(
        (year, total) -> annual.add(new DividendGrowthResult.AnnualDividend(year, total)));

    int lastYear = ((TreeMap<Integer, BigDecimal>) byYear).lastKey();
    BigDecimal[] cagr = new BigDecimal[HORIZONS.length];
    for (int i = 0; i < HORIZONS.length; i++) {
      cagr[i] = cagr(byYear, lastYear, HORIZONS[i]);
    }
    return new DividendGrowthResult(symbol, annual, cagr[0], cagr[1], cagr[2], cagr[3]);
  }

  private BigDecimal cagr(Map<Integer, BigDecimal> byYear, int lastYear, int horizon) {
    BigDecimal end = byYear.get(lastYear);
    BigDecimal start = byYear.get(lastYear - horizon);
    if (start == null || start.signum() <= 0 || end == null || end.signum() <= 0) {
      return null;
    }
    double ratio = end.divide(start, 12, RoundingMode.HALF_UP).doubleValue();
    double rate = Math.pow(ratio, 1.0 / horizon) - 1.0;
    return BigDecimal.valueOf(rate).setScale(CAGR_SCALE, RoundingMode.HALF_UP);
  }
}
