package com.mx.cryptomonitor.domain.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mx.cryptomonitor.domain.models.PortfolioEntry;
import com.mx.cryptomonitor.domain.models.User;
import com.mx.cryptomonitor.shared.dto.response.PortfolioEntryResponse;

import java.util.Optional;
import java.util.UUID;

public interface PortfolioEntryRepository extends JpaRepository<PortfolioEntry, UUID> {
    Optional<PortfolioEntry> findByUserIdAndAssetSymbol(UUID userId, String assetSymbol);

}
