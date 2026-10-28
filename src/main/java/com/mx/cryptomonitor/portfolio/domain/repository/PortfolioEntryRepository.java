package com.mx.cryptomonitor.portfolio.domain.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mx.cryptomonitor.portfolio.domain.model.PortfolioEntry;

public interface PortfolioEntryRepository extends JpaRepository<PortfolioEntry, UUID> {

  List<PortfolioEntry> findByUserId(UUID userId);

  Optional<PortfolioEntry> findByUserIdAndAssetSymbol(UUID userId, String assetSymbol);

  // Nuevo mÃ©todo para obtener sÃ­mbolos de activos Ãºnicos
  @Query("SELECT DISTINCT p.assetSymbol FROM PortfolioEntry p")
  List<String> findDistinctAssetSymbols();

  @Query("SELECT DISTINCT p.userId FROM PortfolioEntry p")
  List<UUID> findDistinctUserIds();

  // Nuevo mÃ©todo para obtener entradas por sÃ­mbolo de activo
  @Query("SELECT p FROM PortfolioEntry p WHERE p.assetSymbol = :symbol")
  List<PortfolioEntry> findByAssetSymbol(@Param("symbol") String symbol);
}
