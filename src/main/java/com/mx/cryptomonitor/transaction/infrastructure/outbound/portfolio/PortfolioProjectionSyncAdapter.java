package com.mx.cryptomonitor.transaction.infrastructure.outbound.portfolio;

import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.stereotype.Component;

import com.mx.cryptomonitor.portfolio.application.port.in.PortfolioChartPort;
import com.mx.cryptomonitor.portfolio.application.port.in.PortfolioEntryPort;
import com.mx.cryptomonitor.portfolio.application.port.in.PortfolioQueryPort;
import com.mx.cryptomonitor.transaction.application.port.out.PortfolioProjectionSyncPort;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class PortfolioProjectionSyncAdapter implements PortfolioProjectionSyncPort {

  private final PortfolioEntryPort portfolioEntryPort;
  private final PortfolioChartPort portfolioChartPort;
  private final PortfolioQueryPort portfolioQueryPort;

  @Override
  public void reconcileUserPortfolio(UUID userId) {
    portfolioEntryPort.reconcilePortfolio(userId);
  }

  @Override
  public void recordUserPortfolioSnapshot(UUID userId) {
    portfolioChartPort.recordEquitySnapshot(userId);
  }

  @Override
  public Optional<UUID> resolvePortfolioEntryId(UUID userId, String assetSymbol) {
    try {
      return portfolioQueryPort.getPortfolioEntryIdByUserAndSymbol(userId, assetSymbol);
    } catch (EmptyResultDataAccessException ex) {
      return Optional.empty();
    }
  }
}
