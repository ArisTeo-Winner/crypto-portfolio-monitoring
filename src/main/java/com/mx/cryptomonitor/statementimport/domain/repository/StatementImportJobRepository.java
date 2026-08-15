package com.mx.cryptomonitor.statementimport.domain.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.mx.cryptomonitor.statementimport.domain.model.StatementImportJob;

@Repository
public interface StatementImportJobRepository extends JpaRepository<StatementImportJob, UUID> {

  Optional<StatementImportJob> findByIdAndUserId(UUID id, UUID userId);

  List<StatementImportJob> findFirst20ByUserIdOrderByCreatedAtDesc(UUID userId);

  /**
   * Reclama hasta {@code limit} jobs QUEUED cuyo backoff ya expiro (o PROCESSING abandonados desde
   * antes de {@code staleBefore}, por si una instancia murio a medio procesar) usando FOR UPDATE
   * SKIP LOCKED: cada instancia del backend que corre esta query en paralelo toma filas distintas
   * sin necesitar coordinacion adicional. Debe invocarse dentro de una transaccion que tambien
   * persista el nuevo status PROCESSING antes de hacer commit (ver
   * StatementImportJobLifecycleService.claimBatch()).
   */
  @Query(
      value =
          "SELECT * FROM statement_import_job "
              + "WHERE (status = 'QUEUED' AND (next_attempt_at IS NULL OR next_attempt_at <= :now)) "
              + "   OR (status = 'PROCESSING' AND started_at < :staleBefore) "
              + "ORDER BY created_at "
              + "LIMIT :limit "
              + "FOR UPDATE SKIP LOCKED",
      nativeQuery = true)
  List<StatementImportJob> lockCandidates(
      @Param("now") OffsetDateTime now,
      @Param("staleBefore") OffsetDateTime staleBefore,
      @Param("limit") int limit);
}
