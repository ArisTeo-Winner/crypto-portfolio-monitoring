package com.mx.cryptomonitor.marketdata.domain.repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.mx.cryptomonitor.marketdata.domain.model.DividendSyncJob;
import com.mx.cryptomonitor.marketdata.domain.model.DividendSyncJobStatus;

/** Cola durable de procesamiento de dividendos (ADR-0028 F2). */
@Repository
public interface DividendSyncJobRepository extends JpaRepository<DividendSyncJob, UUID> {

  /** Dedup al encolar: evita duplicar un símbolo que ya está activo en la cola. */
  boolean existsByAssetSymbolAndStatusIn(
      String assetSymbol, Collection<DividendSyncJobStatus> statuses);

  /**
   * Reclama hasta {@code limit} jobs QUEUED cuyo backoff ya expiró (o PROCESSING abandonados desde
   * antes de {@code staleBefore}, por si una instancia murió a medio procesar) con FOR UPDATE SKIP
   * LOCKED: varias instancias toman filas distintas sin coordinación. Debe correr dentro de una
   * transacción que persista el nuevo estado PROCESSING antes del commit.
   */
  @Query(
      value =
          "SELECT * FROM dividend_sync_job "
              + "WHERE (status = 'QUEUED' AND (next_attempt_at IS NULL OR next_attempt_at <= :now)) "
              + "   OR (status = 'PROCESSING' AND started_at < :staleBefore) "
              + "ORDER BY created_at "
              + "LIMIT :limit "
              + "FOR UPDATE SKIP LOCKED",
      nativeQuery = true)
  List<DividendSyncJob> lockCandidates(
      @Param("now") OffsetDateTime now,
      @Param("staleBefore") OffsetDateTime staleBefore,
      @Param("limit") int limit);
}
