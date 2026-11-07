package com.mx.cryptomonitor.marketdata.domain.model;

/**
 * Estado de un job de la cola durable de dividendos (ADR-0028 F2). {@code QUEUED} espera ser
 * reclamado (respetando {@code next_attempt_at}); {@code PROCESSING} está siendo trabajado; {@code
 * COMPLETED} terminó; {@code DEAD_LETTER} agotó reintentos (el worker ya no lo reclama).
 */
public enum DividendSyncJobStatus {
  QUEUED,
  PROCESSING,
  COMPLETED,
  DEAD_LETTER
}
