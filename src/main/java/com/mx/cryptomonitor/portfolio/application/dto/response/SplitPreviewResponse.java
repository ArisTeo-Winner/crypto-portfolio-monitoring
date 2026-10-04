package com.mx.cryptomonitor.portfolio.application.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Resultado del preview de split (ADR-0011). {@code splitDetected=false} => la operacion se guarda
 * tal cual (no hay ajuste). Si hay split, {@code adjusted} trae cantidad/precio en terminos
 * post-split (cantidad x factor, precio / factor); el costo total no cambia. El sistema NO persiste
 * estos valores ajustados: la transaccion se guarda cruda y la proyeccion aplica el factor en
 * lectura. Este preview es solo transparencia para el usuario.
 */
public record SplitPreviewResponse(
    boolean splitDetected,
    String splitType,
    BigDecimal factor,
    List<AppliedSplit> splits,
    Amounts original,
    Amounts adjusted,
    String note) {

  public record AppliedSplit(String ratio, LocalDate executionDate, BigDecimal shareMultiplier) {}

  public record Amounts(BigDecimal quantity, BigDecimal pricePerUnit) {}

  public static SplitPreviewResponse none(BigDecimal quantity, BigDecimal pricePerUnit) {
    Amounts amounts = new Amounts(quantity, pricePerUnit);
    return new SplitPreviewResponse(false, null, BigDecimal.ONE, List.of(), amounts, amounts, null);
  }
}
