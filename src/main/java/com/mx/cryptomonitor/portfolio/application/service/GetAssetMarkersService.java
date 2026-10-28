package com.mx.cryptomonitor.portfolio.application.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.mx.cryptomonitor.marketdata.application.port.out.StockSplitPort;
import com.mx.cryptomonitor.portfolio.application.port.in.GetAssetMarkersUseCase;
import com.mx.cryptomonitor.portfolio.application.port.out.PortfolioMarkersPort;
import com.mx.cryptomonitor.portfolio.application.port.out.PortfolioTransactionSnapshot;
import com.mx.cryptomonitor.portfolio.domain.exception.PortfolioInvalidRequestException;
import com.mx.cryptomonitor.portfolio.domain.model.PortfolioMarker;
import com.mx.cryptomonitor.portfolio.domain.model.PortfolioMarkerFactory;

@Service
public class GetAssetMarkersService implements GetAssetMarkersUseCase {

  private static final int COST_SCALE = 8;
  private static final StockSplitPort NO_SPLITS = symbol -> List.of();

  private final PortfolioMarkersPort portfolioMarkersPort;
  private final StockSplitPort stockSplitPort;
  private final Clock clock;

  @Autowired
  public GetAssetMarkersService(
      PortfolioMarkersPort portfolioMarkersPort, StockSplitPort stockSplitPort) {
    this(portfolioMarkersPort, stockSplitPort, Clock.systemUTC());
  }

  public GetAssetMarkersService(PortfolioMarkersPort portfolioMarkersPort) {
    this(portfolioMarkersPort, NO_SPLITS, Clock.systemUTC());
  }

  public GetAssetMarkersService(PortfolioMarkersPort portfolioMarkersPort, Clock clock) {
    this(portfolioMarkersPort, NO_SPLITS, clock);
  }

  public GetAssetMarkersService(
      PortfolioMarkersPort portfolioMarkersPort, StockSplitPort stockSplitPort, Clock clock) {
    this.portfolioMarkersPort = portfolioMarkersPort;
    this.stockSplitPort = stockSplitPort;
    this.clock = clock;
  }

  @Override
  public List<PortfolioMarker> getAssetMarkers(UUID userId, String symbol, String range) {
    HoldingsHistoryRange parsedRange = parseRange(range);
    Instant fromInclusive = Instant.now(clock).minus(Duration.ofDays(parsedRange.days()));
    // Un solo simbolo: resolver splitsFor(symbol) una vez y reutilizar (evita O(N) a la DB).
    List<com.mx.cryptomonitor.marketdata.application.port.out.StockSplitData> splits =
        SplitFactors.splitsOf(stockSplitPort, symbol);

    return portfolioMarkersPort.getTransactionsByUserAndSymbol(userId, symbol).stream()
        .filter(this::hasTimestamp)
        .filter(this::isBuyOrSell)
        .filter(snapshot -> isInRange(snapshot, fromInclusive))
        .map(snapshot -> toMarker(snapshot, splits))
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

  private PortfolioMarker toMarker(
      PortfolioTransactionSnapshot snapshot,
      List<com.mx.cryptomonitor.marketdata.application.port.out.StockSplitData> splits) {
    // ADR-0011 Fase 1b: el texto del marker va en terminos post-split (cantidad x factor,
    // precio / factor) para ser consistente con el holding y con la linea ya ajustada.
    java.math.BigDecimal factor = SplitFactors.factorFor(splits, snapshot.transactionDate());
    return PortfolioMarkerFactory.assetMarker(
        snapshot.transactionDate().toInstant(),
        snapshot.transactionType(),
        SplitFactors.adjustQuantity(snapshot.quantity(), factor),
        snapshot.assetSymbol(),
        SplitFactors.adjustPrice(snapshot.pricePerUnit(), factor, COST_SCALE));
  }
}
