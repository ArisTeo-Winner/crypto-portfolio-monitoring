package com.mx.cryptomonitor.asset.domain.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mx.cryptomonitor.asset.domain.model.AssetCatalogEntity;

public interface AssetCatalogRepository extends JpaRepository<AssetCatalogEntity, String> {

  List<AssetCatalogEntity> findByAssetType(String assetType);

  List<AssetCatalogEntity> findByPopularTrue();

  /** Sucesora de un ticker (ADR-0014 F6a): la fila cuyo predecessor_symbol apunta a este. */
  Optional<AssetCatalogEntity> findByPredecessorSymbol(String predecessorSymbol);

  /** Catalogo por estado de plaza (p.ej. listar lo MUTED_BY_MERGER o DELISTED). */
  List<AssetCatalogEntity> findByMarketStatus(String marketStatus);
}
