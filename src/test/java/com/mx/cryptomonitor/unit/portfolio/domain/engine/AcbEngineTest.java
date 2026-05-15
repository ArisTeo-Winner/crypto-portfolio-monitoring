package com.mx.cryptomonitor.unit.portfolio.domain.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.mx.cryptomonitor.portfolio.domain.engine.AcbEngine;

class AcbEngineTest {

  @Test
  void sellUsesAverageCostBasisIncludingBuyFees() {
    AcbEngine engine = new AcbEngine();
    engine.buy(new BigDecimal("2"), new BigDecimal("100"), new BigDecimal("0.50"));
    engine.buy(new BigDecimal("1"), new BigDecimal("130"), BigDecimal.ZERO);

    BigDecimal realized =
        engine.sell(new BigDecimal("1.5"), new BigDecimal("140"), new BigDecimal("1.00"));

    assertThat(realized).isEqualByComparingTo("43.75");
    assertThat(engine.holdings()).isEqualByComparingTo("1.5");
    assertThat(engine.averageCost()).isEqualByComparingTo("110.16666666");
  }
}
