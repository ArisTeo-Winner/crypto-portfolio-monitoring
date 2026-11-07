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
    NextDividend nextDividend) {

  public record AnnualDividendPoint(int year, BigDecimal totalDps) {}

  /** Próximo dividendo anunciado (ADR-0026): pago aún no realizado. */
  public record NextDividend(
      LocalDate exDividendDate,
      LocalDate paymentDate,
      BigDecimal amountPerShare,
      String currency,
      Short frequency) {}
}
