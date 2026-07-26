package com.mx.cryptomonitor.marketdata.application.port.out;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;

/** Puerto hacia el proveedor de tasas de bonos gubernamentales (Banxico SIE — curva CETES). */
public interface GovBondRatePort {

  /** Curva vigente completa: plazo en dias -> tasa porcentual. */
  Map<Integer, BigDecimal> getCetesCurve();

  /** Tasa del plazo exacto si existe; en caso contrario, la del plazo mas cercano. */
  Optional<BigDecimal> getCetesRate(int termDays);

  /**
   * Tasa de referencia CETES 28 dias (serie SIE {@code SF60633}), valor mas reciente ("oportuno").
   * Es una medida distinta de la curva de subasta primaria, por eso no forma parte de {@link
   * #getCetesCurve()}. Ver ADR-0001.
   */
  Optional<BigDecimal> getReferenceRate();

  /**
   * Resuelve la tasa del plazo mas cercano a {@code termDays} dentro de {@code curve}. En caso de
   * empate entre dos plazos equidistantes, prevalece el primero encontrado segun el orden de
   * iteracion del mapa.
   */
  static Optional<BigDecimal> nearestRate(Map<Integer, BigDecimal> curve, int termDays) {
    if (curve == null || curve.isEmpty()) {
      return Optional.empty();
    }
    BigDecimal exact = curve.get(termDays);
    if (exact != null) {
      return Optional.of(exact);
    }
    return curve.entrySet().stream()
        .min(Comparator.comparingInt(entry -> Math.abs(entry.getKey() - termDays)))
        .map(Map.Entry::getValue);
  }
}
