package com.mx.cryptomonitor.portfolio.application.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.mx.cryptomonitor.portfolio.application.port.in.GetAssetMarkersUseCase;
import com.mx.cryptomonitor.portfolio.application.port.out.PortfolioMarkersPort;
import com.mx.cryptomonitor.portfolio.application.port.out.PortfolioTransactionSnapshot;
import com.mx.cryptomonitor.portfolio.domain.exception.PortfolioInvalidRequestException;
import com.mx.cryptomonitor.portfolio.domain.model.PortfolioMarker;
import com.mx.cryptomonitor.portfolio.domain.model.PortfolioMarkerFactory;

@Service
public class GetAssetMarkersService implements GetAssetMarkersUseCase {

  private final PortfolioMarkersPort portfolioMarkersPort;
  private final Clock clock;

  @Autowired
  public GetAssetMarkersService(PortfolioMarkersPort portfolioMarkersPort) {
    this(portfolioMarkersPort, Clock.systemUTC());
  }

  public GetAssetMarkersService(PortfolioMarkersPort portfolioMarkersPort, Clock clock) {
    this.portfolioMarkersPort = portfolioMarkersPort;
    this.clock = clock;
  }

  @Override
  public List<PortfolioMarker> getAssetMarkers(UUID userId, String symbol, String range) {
    HoldingsHistoryRange parsedRange = parseRange(range);
    Instant fromInclusive = Instant.now(clock).minus(Duration.ofDays(parsedRange.days()));

    return portfolioMarkersPort.getTransactionsByUserAndSymbol(userId, symbol).stream()
        .filter(this::hasTimestamp)
        .filter(this::isBuyOrSell)
        .filter(snapshot -> isInRange(snapshot, fromInclusive))
        .map(this::toMarker)
        .toList();
  }

  private HoldingsHistoryRange parseRange(String range) {
    try {
      return HoldingsHistoryRange.parse(range);
    } catch (IllegalArgumentException ex) {
      throw new PortfolioInvalidRequestException(ex.getMessage(), ex);
    }
  }

  private boolean hasTimestamp(PortfolioTransactionSnapshot snapshot) {
    return snapshot.transactionDate() != null;
  }

  private boolean isBuyOrSell(PortfolioTransactionSnapshot snapshot) {
    String type = snapshot.transactionType();
    return type != null
        && ("BUY".equalsIgnoreCase(type.trim()) || "SELL".equalsIgnoreCase(type.trim()));
  }

  private boolean isInRange(PortfolioTransactionSnapshot snapshot, Instant fromInclusive) {
    return !snapshot.transactionDate().toInstant().isBefore(fromInclusive);
  }

  private PortfolioMarker toMarker(PortfolioTransactionSnapshot snapshot) {
    return PortfolioMarkerFactory.assetMarker(
        snapshot.transactionDate().toInstant(),
        snapshot.transactionType(),
        snapshot.quantity(),
        snapshot.assetSymbol(),
        snapshot.pricePerUnit());
  }
}
