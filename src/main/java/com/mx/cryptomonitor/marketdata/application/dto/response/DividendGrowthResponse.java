package com.mx.cryptomonitor.marketdata.application.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Crecimiento del dividendo de una seguridad (ADR-0014 F5). {@code annualDividends} = DPS por año
 * completo (recurring); {@code cagr*} = tasa compuesta anual por horizonte como fracción (0.0734 =
 * 7.34%), o {@code null} si no hay historia suficiente.
 *
 * <p>ADR-0025 F4: {@code eligibilityStatus} indica si el activo paga dividendos sin que el front lo
 * tenga que inferir del vacío — {@code PAYS} | {@code NONE} (confirmado que no paga) | {@code
 * ERROR} (proveedor no concluyó, re-intentar) | {@code UNKNOWN} (aún no verificado). {@code
 * lastCheckedAt} es cuándo se resolvió por última vez ({@code null} si {@code UNKNOWN}). Permite el
 * badge "paga / no paga / verificando".
 *
 * <p>ADR-0026: {@code nextDividend} = el próximo dividendo anunciado (pago futuro), o {@code null}
 * si no hay uno. Consolida la fila de la tabla "Dividends" (próximo pago / ex-date / monto por
 * acción) en la misma llamada por holding.
 *
 * <p>ADR-0031: {@code yieldPct} = rendimiento por dividendo como fracción (0.0189 = 1.89%),
 * calculado en el backend en la <b>moneda nativa</b> del activo — DPS anual (último año completo) ÷
 * precio actual en esa misma moneda. Al computarse en moneda nativa, el porcentaje es invariante al
 * tipo de cambio y no depende de la {@code preferredCurrency} del usuario; esto disuelve el
 * problema cross-currency que tenía el front al dividir dividendo y valor en monedas distintas.
 * {@code priceBasis} expone el precio y la moneda con que se calculó. Ambos son {@code null} si no
 * hay historia de dividendos o no se pudo obtener precio (el porcentaje no se inventa).
 */
public record DividendGrowthResponse(
    String symbol,
    List<AnnualDividendPoint> annualDividends,
    BigDecimal cagr1y,
    BigDecimal cagr3y,
    BigDecimal cagr5y,
    BigDecimal cagr10y,
    String currency,
    String eligibilityStatus,
    OffsetDateTime lastCheckedAt,
    NextDividend nextDividend,
    BigDecimal yieldPct,
    PriceBasis priceBasis) {

  public record AnnualDividendPoint(int year, BigDecimal totalDps) {}

  /**
   * ADR-0031: precio con el que se calculó {@link #yieldPct}. {@code currency} coincide con la
   * moneda del dividendo (cálculo en moneda nativa); {@code asOf} es la marca de tiempo del precio.
   */
  public record PriceBasis(BigDecimal price, String currency, OffsetDateTime asOf) {}

  /** Próximo dividendo anunciado (ADR-0026): pago aún no realizado. */
  public record NextDividend(
      LocalDate exDividendDate,
      LocalDate paymentDate,
      BigDecimal amountPerShare,
      String currency,
      Short frequency) {}
}
