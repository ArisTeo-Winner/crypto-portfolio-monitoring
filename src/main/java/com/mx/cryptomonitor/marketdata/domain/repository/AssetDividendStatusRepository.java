package com.mx.cryptomonitor.marketdata.domain.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mx.cryptomonitor.marketdata.domain.model.AssetDividendStatusEntity;

/** Acceso al estado de elegibilidad de dividendos por símbolo (ADR-0025 F1). */
public interface AssetDividendStatusRepository
    extends JpaRepository<AssetDividendStatusEntity, UUID> {

  /** Estado actual de un símbolo (normalizado). Vacío = {@code UNKNOWN} (aún no verificado). */
  Optional<AssetDividendStatusEntity> findByAssetSymbol(String assetSymbol);
}
