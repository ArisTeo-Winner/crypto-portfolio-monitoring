package com.mx.cryptomonitor.portfolio.infrastructure.outbound.persistence;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.mx.cryptomonitor.portfolio.application.port.out.PortfolioAssetUniversePort;
import com.mx.cryptomonitor.portfolio.domain.model.AssetType;
import com.mx.cryptomonitor.portfolio.domain.repository.PortfolioEntryRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PortfolioAssetUniverseAdapter implements PortfolioAssetUniversePort {

  private final PortfolioEntryRepository portfolioEntryRepository;

  @Override
  public List<PortfolioAssetReference> getAssetsByUser(UUID userId) {
    return portfolioEntryRepository.findByUserId(userId).stream()
        .map(
            entry ->
                new PortfolioAssetReference(
                    AssetType.from(entry.getAssetType()),
                    entry.getAssetSymbol().trim().toUpperCase(Locale.ROOT)))
        .distinct()
        .toList();
  }
}
