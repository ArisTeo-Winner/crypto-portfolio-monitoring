package com.mx.cryptomonitor.portfolio.domain.engine;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.mx.cryptomonitor.portfolio.domain.model.PricePoint;

/**
 * Valua un instrumento de renta fija a descuento (p. ej. CETES) por devengo lineal determinista,
 * sin depender de un proveedor de precios externo.
 *
 * <p>El precio unitario devengado al día {@code d} es:
 *
 * <pre>
 *   precioUnit(d) = costoUnit + (faceUnit - costoUnit) * clamp(diasTranscurridos, 0, plazo) / plazo
 *   plazo = días(vencimiento - fechaOperación)
 * </pre>
 *
 * Antes de la fecha de operación el precio queda en el costo; en o después del vencimiento queda en
 * el valor nominal (face). La fecha ancla es la de la operación (Evento1), no la de registro, de
 * modo que el modelo es indiferente al rezago con que el usuario registre la compra.
 *
 * <p>Emite un punto por día UTC en {@code [start, end]} (alineado a medianoche); corriente arriba
 * el {@code HoldingsValueCalculator} multiplica por la cantidad viva, así que la salida/redención
 * la da la línea de cantidad (no este valuador).
 */
public class FixedIncomeAccrualValuator {

  private static final MathContext MC = new MathContext(20, RoundingMode.HALF_UP);
  private static final long SECONDS_PER_DAY = 86_400L;

  public List<PricePoint> accruedPriceSeries(
      BigDecimal costPerUnit,
      BigDecimal facePerUnit,
      Instant operationDate,
      Instant maturityDate,
      Instant start,
      Instant end) {
    long operationDay = alignToDay(operationDate.getEpochSecond());
    long maturityDay = alignToDay(maturityDate.getEpochSecond());
    long termDays = (maturityDay - operationDay) / SECONDS_PER_DAY;

    long startDay = alignToDay(start.getEpochSecond());
    long endDay = alignToDay(end.getEpochSecond());

    List<PricePoint> series = new ArrayList<>();
    for (long day = startDay; day <= endDay; day += SECONDS_PER_DAY) {
      series.add(
          new PricePoint(
              Instant.ofEpochSecond(day),
              priceAt(costPerUnit, facePerUnit, operationDay, termDays, day)));
    }
    return series;
  }

  private BigDecimal priceAt(
      BigDecimal costPerUnit, BigDecimal facePerUnit, long operationDay, long termDays, long day) {
    // Vencimiento el mismo día de la operación (o antes): no hay devengo, vale el nominal.
    if (termDays <= 0) {
      return facePerUnit;
    }
    long elapsedDays = (day - operationDay) / SECONDS_PER_DAY;
    long clampedDays = Math.max(0L, Math.min(elapsedDays, termDays));
    BigDecimal fraction = BigDecimal.valueOf(clampedDays).divide(BigDecimal.valueOf(termDays), MC);
    return costPerUnit.add(facePerUnit.subtract(costPerUnit).multiply(fraction, MC), MC);
  }

  private static long alignToDay(long epochSeconds) {
    return epochSeconds - Math.floorMod(epochSeconds, SECONDS_PER_DAY);
  }
}
