package com.mx.cryptomonitor.portfolio.domain.engine;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.mx.cryptomonitor.portfolio.domain.model.PricePoint;
import com.mx.cryptomonitor.portfolio.domain.model.QuantityTimelinePoint;
import com.mx.cryptomonitor.portfolio.domain.model.TimeValuePoint;

public class HoldingsValueCalculator {

  private static final int MONEY_SCALE = 2;

  public List<TimeValuePoint> calculate(
      List<PricePoint> priceSeries, List<QuantityTimelinePoint> quantityTimeline) {
    List<QuantityTimelinePoint> quantityEvents =
        quantityTimeline.stream().sorted(Comparator.comparing(QuantityTimelinePoint::time)).toList();
    List<PricePoint> orderedPrices =
        priceSeries.stream().sorted(Comparator.comparing(PricePoint::time)).toList();

    List<TimeValuePoint> values = new ArrayList<>();
    BigDecimal currentQuantity = BigDecimal.ZERO;
    int quantityIndex = 0;

    for (PricePoint pricePoint : orderedPrices) {
      while (quantityIndex < quantityEvents.size()
          && !quantityEvents.get(quantityIndex).time().isAfter(pricePoint.time())) {
        currentQuantity = quantityEvents.get(quantityIndex).quantity();
        quantityIndex++;
      }

      BigDecimal value =
          currentQuantity.multiply(pricePoint.price()).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
      values.add(new TimeValuePoint(pricePoint.time().getEpochSecond(), value));
    }

    return values;
  }
}
