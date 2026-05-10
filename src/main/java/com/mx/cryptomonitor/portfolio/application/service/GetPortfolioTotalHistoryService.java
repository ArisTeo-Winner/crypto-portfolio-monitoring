package com.mx.cryptomonitor.portfolio.application.service;

import java.time.Duration;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.mx.cryptomonitor.portfolio.application.port.in.GetPortfolioTotalHistoryUseCase;
import com.mx.cryptomonitor.portfolio.application.port.out.MarketPriceHistoryPort;
import com.mx.cryptomonitor.portfolio.application.port.out.PortfolioTransactionSnapshot;
import com.mx.cryptomonitor.portfolio.application.port.out.TransactionHistoryPort;
import com.mx.cryptomonitor.portfolio.domain.engine.PortfolioHoldingsAggregationEngine;
import com.mx.cryptomonitor.portfolio.domain.model.AssetType;
import com.mx.cryptomonitor.portfolio.domain.model.HistoricalPriceSeries;
import com.mx.cryptomonitor.portfolio.domain.model.PortfolioAccountingTransaction;
import com.mx.cryptomonitor.portfolio.domain.model.PortfolioAssetHistoryInput;
import com.mx.cryptomonitor.portfolio.domain.model.PricePoint;
import com.mx.cryptomonitor.portfolio.domain.model.TimeValuePoint;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class GetPortfolioTotalHistoryService implements GetPortfolioTotalHistoryUseCase {

  private final TransactionHistoryPort transactionHistoryPort;
  private final MarketPriceHistoryPort marketPriceHistoryPort;
  private final PortfolioHoldingsAggregationEngine aggregationEngine;

  public GetPortfolioTotalHistoryService(
      TransactionHistoryPort transactionHistoryPort, MarketPriceHistoryPort marketPriceHistoryPort) {
    this.transactionHistoryPort = transactionHistoryPort;
    this.marketPriceHistoryPort = marketPriceHistoryPort;
    this.aggregationEngine = new PortfolioHoldingsAggregationEngine();
  }

  @Override
  public List<TimeValuePoint> getTotalHistory(UUID userId, String range, String assetTypes) {
    long started = System.nanoTime();
    HoldingsHistoryRange parsedRange = HoldingsHistoryRange.parse(range);
    List<PortfolioTransactionSnapshot> snapshots =
        filterByAssetTypes(transactionHistoryPort.getTransactionsByUser(userId), parseAssetTypes(assetTypes));
    if (snapshots.isEmpty()) {
      logGenerated(userId, 0, 0, started);
      return List.of();
    }

    List<PortfolioAssetHistoryInput> assets =
        snapshots.stream()
            .collect(
                java.util.stream.Collectors.groupingBy(
                    snapshot -> new AssetIdentity(AssetType.from(snapshot.assetType()), snapshot.assetSymbol()),
                    LinkedHashMap::new,
                    java.util.stream.Collectors.toList()))
            .entrySet()
            .stream()
            .map(entry -> toAssetInput(entry.getKey(), entry.getValue(), parsedRange))
            .filter(input -> !input.priceSeries().points().isEmpty())
            .sorted(Comparator.comparing(PortfolioAssetHistoryInput::symbol))
            .toList();

    List<TimeValuePoint> series = aggregationEngine.aggregate(assets);
    logGenerated(userId, assets.size(), series.size(), started);
    return series;
  }

  private PortfolioAssetHistoryInput toAssetInput(
      AssetIdentity identity,
      List<PortfolioTransactionSnapshot> snapshots,
      HoldingsHistoryRange range) {
    List<PricePoint> prices =
        marketPriceHistoryPort.getPriceHistory(identity.assetType(), identity.symbol(), range.value());
    return new PortfolioAssetHistoryInput(
        identity.assetType(),
        identity.symbol(),
        snapshots.stream().map(this::toAccountingTransaction).toList(),
        new HistoricalPriceSeries(identity.assetType(), identity.symbol(), prices));
  }

  private PortfolioAccountingTransaction toAccountingTransaction(
      PortfolioTransactionSnapshot snapshot) {
    return new PortfolioAccountingTransaction(
        snapshot.transactionDate().toInstant(ZoneOffset.UTC),
        snapshot.assetSymbol(),
        AssetType.from(snapshot.assetType()),
        snapshot.transactionType(),
        snapshot.quantity(),
        snapshot.pricePerUnit(),
        snapshot.totalValue(),
        snapshot.fee());
  }

  private List<PortfolioTransactionSnapshot> filterByAssetTypes(
      List<PortfolioTransactionSnapshot> snapshots, EnumSet<AssetType> requestedTypes) {
    if (requestedTypes == null) {
      return snapshots;
    }
    return snapshots.stream()
        .filter(snapshot -> requestedTypes.contains(AssetType.from(snapshot.assetType())))
        .toList();
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

  private void logGenerated(UUID userId, int assetCount, int timePoints, long started) {
    long durationMs = Duration.ofNanos(System.nanoTime() - started).toMillis();
    log.info(
        "portfolio.history.total.generated userId={} assetCount={} timePoints={} durationMs={}",
        userId,
        assetCount,
        timePoints,
        durationMs);
  }

  private record AssetIdentity(AssetType assetType, String symbol) {

    private AssetIdentity {
      symbol = symbol.trim().toUpperCase(Locale.ROOT);
    }
  }
}
