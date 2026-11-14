package com.mx.cryptomonitor.marketdata.domain.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mx.cryptomonitor.marketdata.domain.model.AssetDividendStatusEntity;

/** Acceso al estado de elegibilidad de dividendos por símbolo (ADR-0025 F1). */
public interface AssetDividendStatusRepository
    extends JpaRepository<AssetDividendStatusEntity, UUID> {

  /** Estado actual de un símbolo (normalizado). Vacío = {@code UNKNOWN} (aún no verificado). */
  Optional<AssetDividendStatusEntity> findByAssetSymbol(String assetSymbol);

  /**
   * Símbolos vencidos para re-chequeo (ADR-0025 F4-B), por cadencia según estado: {@code NONE}
   * puede empezar a pagar, {@code ERROR} es no concluyente (reintentar pronto), {@code PAYS} se
   * refresca para descubrir nuevos pagos. Cada estado usa su propio umbral; ordenado por el más
   * antiguo y limitado por {@code Pageable} para acotar el trabajo por corrida (batching).
   */
  @Query(
      "SELECT s FROM AssetDividendStatusEntity s WHERE"
          + " (s.status = com.mx.cryptomonitor.marketdata.domain.model"
          + ".DividendEligibilityStatus.NONE AND s.lastCheckedAt < :noneBefore)"
          + " OR (s.status = com.mx.cryptomonitor.marketdata.domain.model"
          + ".DividendEligibilityStatus.ERROR AND s.lastCheckedAt < :errorBefore)"
          + " OR (s.status = com.mx.cryptomonitor.marketdata.domain.model"
          + ".DividendEligibilityStatus.PAYS AND s.lastCheckedAt < :paysBefore)"
          + " ORDER BY s.lastCheckedAt ASC")
  List<AssetDividendStatusEntity> findDueForRecheck(
      @Param("noneBefore") OffsetDateTime noneBefore,
      @Param("errorBefore") OffsetDateTime errorBefore,
      @Param("paysBefore") OffsetDateTime paysBefore,
      Pageable pageable);
}
