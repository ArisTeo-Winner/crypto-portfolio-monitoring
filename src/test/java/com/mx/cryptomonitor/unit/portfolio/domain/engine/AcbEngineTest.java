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

  @Test
  void weightedAverageOfTwoFractionalBuysStaysExact() {
    AcbEngine engine = new AcbEngine();
    // Compra 1: fraccion USA (DriveWealth), 5 decimales.
    engine.buy(new BigDecimal("377.13248"), new BigDecimal("0.10993"), new BigDecimal("0.10"));
    // Compra 2: otra fraccion del mismo activo.
    engine.buy(new BigDecimal("123.45678"), new BigDecimal("0.12000"), new BigDecimal("0.05"));

    // Acumulacion EXACTA: 377.13248 + 123.45678 = 500.58926, sin basura de punto flotante.
    // Con double la suma podria dar 500.58926000000004 y alterar el balance; BigDecimal lo impide.
    assertThat(engine.holdings()).isEqualByComparingTo("500.58926");
    assertThat(engine.holdings().toPlainString()).isEqualTo("500.58926");

    // Promedio ponderado = costoTotal / cantidadTotal (escala 8), derivado on-demand desde los
    // totales exactos, nunca acumulado desde promedios de lote redondeados.
    assertThat(engine.averageCost()).isEqualByComparingTo("0.11271314");
  }
}
