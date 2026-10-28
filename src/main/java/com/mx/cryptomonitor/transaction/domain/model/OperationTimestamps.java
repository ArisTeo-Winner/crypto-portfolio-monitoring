package com.mx.cryptomonitor.transaction.domain.model;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

/**
 * Helper de ADR-0013. El evento de operacion bursatil se persiste como binomio inmutable
 * (wall-clock local de la plaza + nombre IANA de la zona), nunca como offset ni instante
 * precomputado: un instante guardado se corre solo si cambian las reglas de DST. El instante UTC
 * para ordenar/indexar se DERIVA aqui, resolviendo el nombre de zona (que aplica el DST correcto de
 * la fecha).
 */
public final class OperationTimestamps {

  private OperationTimestamps() {}

  /**
   * Resuelve el binomio (wall-clock + nombre IANA) al instante UTC derivado. Devuelve {@code null}
   * si el binomio esta incompleto (p. ej. fila aun no migrada).
   */
  public static OffsetDateTime resolveUtc(
      LocalDateTime localExecutionTime, String executionZoneId) {
    if (localExecutionTime == null || executionZoneId == null || executionZoneId.isBlank()) {
      return null;
    }
    return ZonedDateTime.of(localExecutionTime, ZoneId.of(executionZoneId))
        .toInstant()
        .atOffset(ZoneOffset.UTC);
  }

  /**
   * Fija el binomio en la transaccion y recalcula {@code executedAtUtc} (instante derivado para
   * orden). Mantiene la columna derivada siempre consistente con la fuente de verdad.
   */
  public static void apply(
      Transaction transaction, LocalDateTime localExecutionTime, String executionZoneId) {
    transaction.setLocalExecutionTime(localExecutionTime);
    transaction.setExecutionZoneId(executionZoneId);
    transaction.setExecutedAtUtc(resolveUtc(localExecutionTime, executionZoneId));
  }
}
