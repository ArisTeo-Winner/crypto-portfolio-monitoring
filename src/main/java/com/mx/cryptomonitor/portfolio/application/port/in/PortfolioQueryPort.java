package com.mx.cryptomonitor.portfolio.application.port.in;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mx.cryptomonitor.portfolio.application.dto.response.PortfolioHoldingsPerformanceResponse;
import com.mx.cryptomonitor.portfolio.domain.model.PortfolioEntry;

public interface PortfolioQueryPort {

  List<PortfolioEntry> getPortfolioEntriesByUser(UUID userId);

  Optional<PortfolioEntry> getPortfolioEntryByUserAndSymbol(UUID userId, String assetSymbol);

  Optional<UUID> getPortfolioEntryIdByUserAndSymbol(UUID userId, String assetSymbol);

  PortfolioHoldingsPerformanceResponse getHoldingsPerformanceByPortfolioId(
      UUID userId, String portfolioId, String period);

  Optional<BigDecimal> getCurrentCryptoPrice(String assetSymbol);

  LocalDateTime now();
}
