package com.mx.cryptomonitor.unit.portfolio.domain.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.mx.cryptomonitor.portfolio.domain.engine.FixedIncomeAccrualValuator;
import com.mx.cryptomonitor.portfolio.domain.model.PricePoint;

class FixedIncomeAccrualValuatorTest {

  private final FixedIncomeAccrualValuator valuator = new FixedIncomeAccrualValuator();

  // CETES de ejemplo: costo 9.80 por título, nominal 10.00, plazo de 10 días.
  private static final BigDecimal COST = new BigDecimal("9.80");
  private static final BigDecimal FACE = new BigDecimal("10.00");
  private static final Instant OPERATION = Instant.parse("2026-01-01T00:00:00Z");
  private static final Instant MATURITY = Instant.parse("2026-01-11T00:00:00Z");

  @Test
  void accruesLinearlyBetweenOperationAndMaturity() {
    Map<Instant, BigDecimal> byDay = priceByDay(seriesOver("2025-12-31", "2026-01-12"));

    // Un punto por día UTC en [start, end] inclusive.
    assertThat(byDay).hasSize(13);
    // Antes de la operación el devengo se fija en el costo.
    assertThat(byDay.get(Instant.parse("2025-12-31T00:00:00Z"))).isEqualByComparingTo("9.80");
    // Fecha de operación: cero devengo.
    assertThat(byDay.get(OPERATION)).isEqualByComparingTo("9.80");
    // Punto medio (5 de 10 días): 9.80 + (10.00-9.80)*0.5 = 9.90.
    assertThat(byDay.get(Instant.parse("2026-01-06T00:00:00Z"))).isEqualByComparingTo("9.90");
    // Vencimiento: valor nominal íntegro.
    assertThat(byDay.get(MATURITY)).isEqualByComparingTo("10.00");
  }

  @Test
  void holdsFaceValueAfterMaturity() {
    Map<Instant, BigDecimal> byDay = priceByDay(seriesOver("2026-01-11", "2026-01-15"));

    assertThat(byDay.values()).allSatisfy(price -> assertThat(price).isEqualByComparingTo("10.00"));
  }

  @Test
  void sameDayMaturityPricesAtFaceWithoutDividingByZero() {
    List<PricePoint> series =
        valuator.accruedPriceSeries(COST, FACE, OPERATION, OPERATION, OPERATION, OPERATION);

    assertThat(series).singleElement().extracting(PricePoint::price).isEqualTo(FACE);
  }

  private List<PricePoint> seriesOver(String start, String end) {
    return valuator.accruedPriceSeries(
        COST,
        FACE,
        OPERATION,
        MATURITY,
        Instant.parse(start + "T00:00:00Z"),
        Instant.parse(end + "T00:00:00Z"));
  }

  private Map<Instant, BigDecimal> priceByDay(List<PricePoint> series) {
    return series.stream().collect(Collectors.toMap(PricePoint::time, PricePoint::price));
  }
}
