package com.mx.cryptomonitor.domain.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mx.cryptomonitor.domain.models.PortfolioEntry;
import com.mx.cryptomonitor.domain.models.User;
import com.mx.cryptomonitor.shared.dto.response.PortfolioEntryResponse;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PortfolioEntryRepository extends JpaRepository<PortfolioEntry, UUID> {
	
    Optional<PortfolioEntry> findByUserIdAndAssetSymbol(UUID userId, String assetSymbol);

    // Nuevo método para obtener símbolos de activos únicos
    @Query("SELECT DISTINCT p.assetSymbol FROM PortfolioEntry p")
    List<String> findDistinctAssetSymbols();
    
    // Nuevo método para obtener entradas por símbolo de activo
    @Query("SELECT p FROM PortfolioEntry p WHERE p.assetSymbol = :symbol")
    List<PortfolioEntry> findByAssetSymbol(@Param("symbol") String symbol);
}
