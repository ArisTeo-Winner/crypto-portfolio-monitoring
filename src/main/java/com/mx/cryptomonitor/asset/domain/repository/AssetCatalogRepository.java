package com.mx.cryptomonitor.asset.domain.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mx.cryptomonitor.asset.domain.model.AssetCatalogEntity;

public interface AssetCatalogRepository extends JpaRepository<AssetCatalogEntity, String> {

  List<AssetCatalogEntity> findByAssetType(String assetType);

  List<AssetCatalogEntity> findByPopularTrue();
}
