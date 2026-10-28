package com.mx.cryptomonitor.portfolio.application.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import com.mx.cryptomonitor.marketdata.application.port.out.StockSplitData;
import com.mx.cryptomonitor.marketdata.application.port.out.StockSplitPort;

/**
 * Factor de split acumulado (ADR-0011) para una operacion, por fecha: producto de los
 * shareMultiplier de los splits del ticker cuya execution_date es posterior a la fecha de la
 * operacion. La cantidad se multiplica por el factor y el precio unitario se divide, dejando
 * price*qty (el costo) invariante. Fuente unica reutilizada por la valuacion spot, la serie
 * historica por activo, el total y holdings-performance; el feed de precios historicos ya viene
 * ajustado (adjusted=true), asi que lo unico que hay que llevar a terminos post-split es la
 * cantidad.
 */
final class SplitFactors {

  private SplitFactors() {}

  static BigDecimal factorFor(
      StockSplitPort stockSplitPort, String assetSymbol, OffsetDateTime transactionDate) {
    if (transactionDate == null) {
      return BigDecimal.ONE;
    }
    return factorFor(splitsOf(stockSplitPort, assetSymbol), transactionDate);
  }

  /**
   * Resuelve los splits de un simbolo de forma null-safe (el {@code StockSplitPort} puede ser null
   * cuando la prueba construye el servicio sin ese colaborador). Ideal para memoizar por request:
   * {@code map.computeIfAbsent(symbol, s -> SplitFactors.splitsOf(port, s))}.
   */
  static List<StockSplitData> splitsOf(StockSplitPort stockSplitPort, String assetSymbol) {
    if (stockSplitPort == null || assetSymbol == null) {
      return List.of();
    }
    return stockSplitPort.splitsFor(assetSymbol);
  }

  /**
   * Factor a partir de una lista de splits ya resuelta. Usar esta sobrecarga cuando se itera sobre
   * muchas operaciones del mismo ticker: resolver {@code splitsFor(symbol)} una sola vez por
   * simbolo (memoizado por request) y reutilizar la lista, evitando O(N) lecturas a la DB.
   */
  static BigDecimal factorFor(List<StockSplitData> splits, OffsetDateTime transactionDate) {
    if (splits == null || splits.isEmpty() || transactionDate == null) {
      return BigDecimal.ONE;
    }
    LocalDate txDate = transactionDate.toLocalDate();
    BigDecimal factor = BigDecimal.ONE;
    for (StockSplitData split : splits) {
      if (split.executionDate() != null
          && split.shareMultiplier() != null
          && txDate.isBefore(split.executionDate())) {
        factor = factor.multiply(split.shareMultiplier());
      }
    }
    return factor;
  }

  static BigDecimal adjustQuantity(BigDecimal quantity, BigDecimal factor) {
    BigDecimal value = quantity != null ? quantity : BigDecimal.ZERO;
    return factor.compareTo(BigDecimal.ONE) == 0 ? value : value.multiply(factor);
  }

  static BigDecimal adjustPrice(BigDecimal price, BigDecimal factor, int scale) {
    BigDecimal value = price != null ? price : BigDecimal.ZERO;
    if (factor.compareTo(BigDecimal.ONE) == 0 || factor.signum() <= 0) {
      return value;
    }
    return value.divide(factor, scale, RoundingMode.HALF_UP);
  }
}
