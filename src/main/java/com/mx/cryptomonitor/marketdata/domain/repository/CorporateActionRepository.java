package com.mx.cryptomonitor.marketdata.domain.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mx.cryptomonitor.marketdata.domain.model.CorporateActionEntity;
import com.mx.cryptomonitor.marketdata.domain.model.CorporateActionType;

/** Acceso a la base de corporate actions (ADR-0014 F2a). */
public interface CorporateActionRepository extends JpaRepository<CorporateActionEntity, UUID> {

  /** Timeline de eventos de una seguridad ("momentos clave"), del mas reciente al mas antiguo. */
  List<CorporateActionEntity> findByAssetSymbolOrderByPivotalDateDesc(String assetSymbol);

  /** Dedup fuerte por id de proveedor (cuando el feed lo entrega). */
  Optional<CorporateActionEntity> findByProviderEventId(String providerEventId);

  /** Dedup natural (simbolo + tipo + fecha pivote) para seeds/manual sin id de proveedor. */
  boolean existsByAssetSymbolAndActionTypeAndPivotalDate(
      String assetSymbol, CorporateActionType actionType, LocalDate pivotalDate);

  /**
   * Lookup natural (simbolo + tipo + fecha pivote) para reconciliar un evento ya almacenado sin id
   * de proveedor (ADR-0020): decide si esta congelado (skip) o es provisional (actualizable).
   */
  Optional<CorporateActionEntity> findByAssetSymbolAndActionTypeAndPivotalDate(
      String assetSymbol, CorporateActionType actionType, LocalDate pivotalDate);
}
