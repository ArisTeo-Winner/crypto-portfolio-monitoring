package com.mx.cryptomonitor.marketdata.application.service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.mx.cryptomonitor.marketdata.application.dto.response.CetesRateTableEntry;

import lombok.RequiredArgsConstructor;

/**
 * Tabla de tasas CETES (plazo / precio / tasa) a partir de la curva de Banxico (fuente
 * autoritativa, ya cacheada por {@link BanxicoCurveService}), replicando la tabla publica de
 * cetesdirecto: precio unitario de un CETE con valor nominal $10, descontado a cupon cero con la
 * tasa vigente de cada plazo.
 *
 * <p><b>Limitacion conocida:</b> el precio se calcula con el plazo NOMINAL (28/91/182/364/728 dias
 * exactos), no con los dias calendario reales restantes al vencimiento del titulo especifico que
 * Banxico subasta esa semana. La tasa siempre coincide con cetesdirecto; el precio puede diferir en
 * 1-2 centavos para 28-364 dias (el titulo real vence 1-2 dias antes/despues del nominal) y de
 * forma mas notoria en 728 dias (~5 centavos), ya que el CETES a 2 anios no se subasta cada semana
 * y el titulo vigente suele tener bastantes menos dias reales que 728. Corregir esto requeriria que
 * Banxico/DataBursatil expusieran la fecha de vencimiento real del titulo, dato que ninguno de los
 * dos proveedores integrados hoy entrega.
 */
@Service
@RequiredArgsConstructor
public class CetesRateTableService {

  private static final Map<Integer, String> TERM_LABELS =
      Map.of(
          28, "1 mes",
          91, "3 meses",
          182, "6 meses",
          364, "1 año",
          728, "2 años");

  private static final BigDecimal FACE_VALUE_UNIT = BigDecimal.TEN;
  private static final BigDecimal DAY_COUNT_BASIS = BigDecimal.valueOf(360);
  private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
  private static final MathContext MC = new MathContext(20, RoundingMode.HALF_UP);

  private final BanxicoCurveService banxicoCurveService;

  public List<CetesRateTableEntry> getCetesTable() {
    Map<Integer, BigDecimal> curve = banxicoCurveService.getCurve();
    if (curve.isEmpty()) {
      return List.of();
    }

    LocalDate fecha = banxicoCurveService.getAuctionDate().orElse(LocalDate.now());
    return curve.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .map(
            entry -> {
              int plazoDias = entry.getKey();
              BigDecimal tasa = entry.getValue();
              return new CetesRateTableEntry(
                  plazoDias,
                  TERM_LABELS.getOrDefault(plazoDias, plazoDias + " dias"),
                  precioCuponCero(tasa, plazoDias),
                  tasa.setScale(4, RoundingMode.HALF_UP),
                  fecha);
            })
        .toList();
  }

  /**
   * precioUnit = 10 / (1 + tasa/100 * dias/360) — descuento cupon cero, valor nominal $10. {@code
   * dias} es el plazo nominal, no los dias reales al vencimiento del titulo vigente (ver limitacion
   * documentada en la clase).
   */
  private BigDecimal precioCuponCero(BigDecimal tasa, int dias) {
    BigDecimal tasaDecimal = tasa.divide(HUNDRED, MC);
    BigDecimal factor = tasaDecimal.multiply(BigDecimal.valueOf(dias)).divide(DAY_COUNT_BASIS, MC);
    BigDecimal denominator = BigDecimal.ONE.add(factor);
    return FACE_VALUE_UNIT.divide(denominator, MC).setScale(2, RoundingMode.HALF_UP);
  }
}
