package com.mx.cryptomonitor.unit.portfolio.domain.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.mx.cryptomonitor.portfolio.domain.engine.FifoEngine;

class FifoEngineTest {

  @Test
  void sellConsumesOldestLotsFirstForPartialSell() {
    FifoEngine engine = new FifoEngine();
    engine.buy(new BigDecimal("2"), new BigDecimal("100"));
    engine.buy(new BigDecimal("1"), new BigDecimal("130"));

    BigDecimal realized = engine.sell(new BigDecimal("2.5"), new BigDecimal("140"), new BigDecimal("1.00"));

    assertThat(realized).isEqualByComparingTo("84.00");
    assertThat(engine.holdings()).isEqualByComparingTo("0.5");
    assertThat(engine.openCostBasis()).isEqualByComparingTo("65.00000000");
  }

  @Test
  void fullSellClearsLots() {
    FifoEngine engine = new FifoEngine();
    engine.buy(new BigDecimal("1"), new BigDecimal("50"));

    assertThat(engine.sell(new BigDecimal("1"), new BigDecimal("75"), BigDecimal.ZERO))
        .isEqualByComparingTo("25.00");
    assertThat(engine.lots()).isEmpty();
  }
}
