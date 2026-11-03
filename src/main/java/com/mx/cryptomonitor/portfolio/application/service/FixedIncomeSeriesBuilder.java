package com.mx.cryptomonitor.portfolio.application.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;

import com.mx.cryptomonitor.portfolio.application.port.out.PortfolioTransactionSnapshot;
import com.mx.cryptomonitor.portfolio.domain.engine.FixedIncomeAccrualValuator;
import com.mx.cryptomonitor.portfolio.domain.model.AssetType;
import com.mx.cryptomonitor.portfolio.domain.model.PricePoint;

/**
 * Traduce las transacciones de una posición de renta fija a la curva de precio unitario devengado
 * que consume {@link com.mx.cryptomonitor.portfolio.domain.engine.HoldingsValueCalculator}. Los
 * lotes de compra se agregan a un costo y un nominal por título promedio; la fecha ancla es la
 * operación más antigua y el vencimiento el más lejano (exacto para el caso CETES de un solo lote
 * mantenido a vencimiento).
 */
final class FixedIncomeSeriesBuilder {

  private static final MathContext MC = new MathContext(20, RoundingMode.HALF_UP);

  private final FixedIncomeAccrualValuator valuator = new FixedIncomeAccrualValuator();

  static boolean isAccrualValued(AssetType assetType) {
    return assetType == AssetType.GOVERNMENT_BOND;
  }

  List<PricePoint> build(List<PortfolioTransactionSnapshot> snapshots, Instant start, Instant end) {
    List<PortfolioTransactionSnapshot> buys =
        snapshots.stream()
            .filter(snapshot -> "BUY".equalsIgnoreCase(snapshot.transactionType()))
            .filter(snapshot -> snapshot.faceValue() != null && snapshot.maturityDate() != null)
            .filter(snapshot -> snapshot.quantity() != null && snapshot.quantity().signum() != 0)
            .toList();
    if (buys.isEmpty()) {
      return List.of();
    }

    BigDecimal quantityTotal =
        buys.stream()
            .map(PortfolioTransactionSnapshot::quantity)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    if (quantityTotal.signum() == 0) {
      return List.of();
    }
    BigDecimal costTotal = buys.stream().map(this::costOf).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal faceTotal =
        buys.stream()
            .map(PortfolioTransactionSnapshot::faceValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    Instant operationDate =
        buys.stream()
            .map(snapshot -> snapshot.transactionDate().toInstant())
            .min(Comparator.naturalOrder())
            .orElseThrow();
    Instant maturityDate =
        buys.stream()
            .map(PortfolioTransactionSnapshot::maturityDate)
            .max(Comparator.naturalOrder())
            .orElseThrow()
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant();

    return valuator.accruedPriceSeries(
        costTotal.divide(quantityTotal, MC),
        faceTotal.divide(quantityTotal, MC),
        operationDate,
        maturityDate,
        start,
        end);
  }

  private BigDecimal costOf(PortfolioTransactionSnapshot snapshot) {
    return snapshot.totalValue() != null ? snapshot.totalValue() : BigDecimal.ZERO;
  }
}
