package com.mx.cryptomonitor.portfolio.application.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.mx.cryptomonitor.marketdata.application.port.out.StockSplitPort;
import com.mx.cryptomonitor.portfolio.application.port.in.GetPortfolioMarkersUseCase;
import com.mx.cryptomonitor.portfolio.application.port.out.PortfolioTransactionSnapshot;
import com.mx.cryptomonitor.portfolio.application.port.out.TransactionHistoryPort;
import com.mx.cryptomonitor.portfolio.domain.model.AssetType;
import com.mx.cryptomonitor.portfolio.domain.model.PortfolioMarker;
import com.mx.cryptomonitor.portfolio.domain.model.PortfolioMarkerFactory;

@Service
public class GetPortfolioMarkersService implements GetPortfolioMarkersUseCase {

  private static final StockSplitPort NO_SPLITS = symbol -> List.of();

  private final TransactionHistoryPort transactionHistoryPort;
  private final StockSplitPort stockSplitPort;
  private final Clock clock;

  @Autowired
  public GetPortfolioMarkersService(
      TransactionHistoryPort transactionHistoryPort, StockSplitPort stockSplitPort) {
    this(transactionHistoryPort, stockSplitPort, Clock.systemUTC());
  }

  public GetPortfolioMarkersService(TransactionHistoryPort transactionHistoryPort) {
    this(transactionHistoryPort, NO_SPLITS, Clock.systemUTC());
  }

  public GetPortfolioMarkersService(TransactionHistoryPort transactionHistoryPort, Clock clock) {
    this(transactionHistoryPort, NO_SPLITS, clock);
  }

  public GetPortfolioMarkersService(
      TransactionHistoryPort transactionHistoryPort, StockSplitPort stockSplitPort, Clock clock) {
    this.transactionHistoryPort = transactionHistoryPort;
    this.stockSplitPort = stockSplitPort;
    this.clock = clock;
  }

  @Override
  public List<PortfolioMarker> getMarkers(UUID userId, String range, String assetTypes) {
    HoldingsHistoryRange parsedRange = HoldingsHistoryRange.parse(range);
    Instant fromInclusive = Instant.now(clock).minus(Duration.ofDays(parsedRange.days()));
    EnumSet<AssetType> requestedTypes = parseAssetTypes(assetTypes);
    // Memo por request: splitsFor(symbol) una vez por simbolo (local => thread-safe).
    java.util.Map<String, List<com.mx.cryptomonitor.marketdata.application.port.out.StockSplitData>>
        splitsBySymbol = new java.util.HashMap<>();

    return transactionHistoryPort.getTransactionsByUser(userId).stream()
        .filter(this::hasTimestamp)
        .filter(this::isBuyOrSell)
        .filter(snapshot -> isInRange(snapshot, fromInclusive))
        .filter(snapshot -> matchesAssetTypes(snapshot, requestedTypes))
        .map(snapshot -> toMarker(snapshot, splitsBySymbol))
        .toList();
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

  private boolean matchesAssetTypes(
      PortfolioTransactionSnapshot snapshot, EnumSet<AssetType> requestedTypes) {
    return requestedTypes == null || requestedTypes.contains(AssetType.from(snapshot.assetType()));
  }

  private PortfolioMarker toMarker(
      PortfolioTransactionSnapshot snapshot,
      java.util.Map<
              String, List<com.mx.cryptomonitor.marketdata.application.port.out.StockSplitData>>
          splitsBySymbol) {
    // ADR-0011 Fase 1b: cantidad del marker en terminos post-split, consistente con la serie.
    List<com.mx.cryptomonitor.marketdata.application.port.out.StockSplitData> splits =
        splitsBySymbol.computeIfAbsent(
            snapshot.assetSymbol(), s -> SplitFactors.splitsOf(stockSplitPort, s));
    java.math.BigDecimal factor = SplitFactors.factorFor(splits, snapshot.transactionDate());
    return PortfolioMarkerFactory.from(
        snapshot.transactionDate().toInstant(),
        snapshot.transactionType(),
        SplitFactors.adjustQuantity(snapshot.quantity(), factor),
        snapshot.assetSymbol());
  }

  private EnumSet<AssetType> parseAssetTypes(String assetTypes) {
    if (assetTypes == null || assetTypes.isBlank()) {
      return null;
    }
    EnumSet<AssetType> parsed = EnumSet.noneOf(AssetType.class);
    for (String rawType : assetTypes.split(",")) {
      String normalized = rawType.trim().toUpperCase(Locale.ROOT);
      if (!normalized.isBlank()) {
        parsed.add(AssetType.from(normalized));
      }
    }
    if (parsed.isEmpty()) {
      throw new IllegalArgumentException("assetTypes must contain at least one asset type");
    }
    return parsed;
  }
}
