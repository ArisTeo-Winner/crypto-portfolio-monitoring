package com.mx.cryptomonitor.tax.domain.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.mx.cryptomonitor.tax.domain.model.AnnualRegimeTax;
import com.mx.cryptomonitor.tax.domain.model.TaxRegime;
import com.mx.cryptomonitor.tax.domain.model.TaxableSale;

/**
 * Agrega las ventas de un año fiscal en un resultado por régimen (ADR-0017 F1, acción 4): ganancia
 * bruta, pérdida bruta, resultado neto, base gravable, pérdida amortizable del año y —cuando el
 * régimen define tasa— el impuesto estimado. Las ganancias y pérdidas se compensan <b>dentro del
 * mismo régimen y año</b> (Art. 129 para bolsa). El encadenamiento multi-año del carryforward se
 * persiste en el siguiente chunk.
 */
public class EquityTaxAggregator {

  private static final int MONEY_SCALE = 2;

  public List<AnnualRegimeTax> aggregate(List<TaxableSale> sales, int fiscalYear) {
    Map<TaxRegime, BigDecimal> grossGain = new EnumMap<>(TaxRegime.class);
    Map<TaxRegime, BigDecimal> grossLoss = new EnumMap<>(TaxRegime.class);

    for (TaxableSale sale : sales) {
      if (sale.fiscalYear() != fiscalYear) {
        continue;
      }
      BigDecimal gain = sale.gain() == null ? BigDecimal.ZERO : sale.gain();
      if (gain.signum() >= 0) {
        grossGain.merge(sale.regime(), gain, BigDecimal::add);
      } else {
        grossLoss.merge(sale.regime(), gain.negate(), BigDecimal::add);
      }
    }

    List<AnnualRegimeTax> results = new ArrayList<>();
    for (TaxRegime regime : regimesPresent(grossGain, grossLoss)) {
      BigDecimal gain = money(grossGain.getOrDefault(regime, BigDecimal.ZERO));
      BigDecimal loss = money(grossLoss.getOrDefault(regime, BigDecimal.ZERO));
      BigDecimal net = gain.subtract(loss);
      BigDecimal taxableBase = net.signum() > 0 ? net : BigDecimal.ZERO.setScale(MONEY_SCALE);
      BigDecimal carryforward =
          net.signum() < 0 ? net.negate() : BigDecimal.ZERO.setScale(MONEY_SCALE);
      BigDecimal rate = regime.cedularRate().orElse(null);
      BigDecimal estimatedTax = rate == null ? null : money(taxableBase.multiply(rate));
      results.add(
          new AnnualRegimeTax(
              fiscalYear, regime, gain, loss, net, taxableBase, carryforward, rate, estimatedTax));
    }
    return results;
  }

  private Iterable<TaxRegime> regimesPresent(
      Map<TaxRegime, BigDecimal> grossGain, Map<TaxRegime, BigDecimal> grossLoss) {
    Map<TaxRegime, Boolean> present = new EnumMap<>(TaxRegime.class);
    grossGain.keySet().forEach(regime -> present.put(regime, Boolean.TRUE));
    grossLoss.keySet().forEach(regime -> present.put(regime, Boolean.TRUE));
    return present.keySet();
  }

  private BigDecimal money(BigDecimal value) {
    return value.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
  }
}
