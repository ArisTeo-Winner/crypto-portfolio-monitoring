package com.mx.cryptomonitor.portfolio.application.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.mx.cryptomonitor.portfolio.application.dto.response.AssetHistoryMarkerResponse;
import com.mx.cryptomonitor.portfolio.application.dto.response.AssetHoldingsHistoryResponse;
import com.mx.cryptomonitor.portfolio.application.port.in.GetAssetHoldingsHistoryUseCase;
import com.mx.cryptomonitor.portfolio.application.port.out.AssetTransactionHistoryPort;
import com.mx.cryptomonitor.portfolio.application.port.out.MarketPriceHistoryPort;
import com.mx.cryptomonitor.portfolio.application.port.out.PortfolioTransactionSnapshot;
import com.mx.cryptomonitor.portfolio.domain.engine.HoldingsValueCalculator;
import com.mx.cryptomonitor.portfolio.domain.engine.PortfolioQuantityTimelineEngine;
import com.mx.cryptomonitor.portfolio.domain.model.AssetType;
import com.mx.cryptomonitor.portfolio.domain.model.ChartResolution;
import com.mx.cryptomonitor.portfolio.domain.model.ChartResolutionStrategy;
import com.mx.cryptomonitor.portfolio.domain.model.PortfolioAccountingTransaction;
import com.mx.cryptomonitor.portfolio.domain.model.PricePoint;
import com.mx.cryptomonitor.portfolio.domain.model.TimeValuePoint;

@Service
public class GetAssetHoldingsHistoryService implements GetAssetHoldingsHistoryUseCase {

  private final MarketPriceHistoryPort marketPriceHistoryPort;
  private final AssetTransactionHistoryPort assetTransactionHistoryPort;
  private final PortfolioQuantityTimelineEngine quantityTimelineEngine;
  private final HoldingsValueCalculator holdingsValueCalculator;
  private final ChartResolutionStrategy chartResolutionStrategy;
  private final Clock clock;

  @Autowired
  public GetAssetHoldingsHistoryService(
      MarketPriceHistoryPort marketPriceHistoryPort,
      AssetTransactionHistoryPort assetTransactionHistoryPort) {
    this(marketPriceHistoryPort, assetTransactionHistoryPort, Clock.systemUTC());
  }

  public GetAssetHoldingsHistoryService(
      MarketPriceHistoryPort marketPriceHistoryPort,
      AssetTransactionHistoryPort assetTransactionHistoryPort,
      Clock clock) {
    this.marketPriceHistoryPort = marketPriceHistoryPort;
    this.assetTransactionHistoryPort = assetTransactionHistoryPort;
    this.quantityTimelineEngine = new PortfolioQuantityTimelineEngine();
    this.holdingsValueCalculator = new HoldingsValueCalculator();
    this.chartResolutionStrategy = new ChartResolutionStrategy();
    this.clock = clock;
  }

  @Override
  public AssetHoldingsHistoryResponse getAssetHoldingsHistory(
      UUID userId, String symbol, String range) {
    HoldingsHistoryRange parsedRange = HoldingsHistoryRange.parse(range);
    List<PortfolioTransactionSnapshot> snapshots =
        assetTransactionHistoryPort.getTransactionsByUserAndSymbol(userId, symbol);
    if (snapshots.isEmpty()) {
      return new AssetHoldingsHistoryResponse(List.of(), List.of());
    }

    AssetType assetType = AssetType.from(snapshots.get(snapshots.size() - 1).assetType());
    Instant end = Instant.now(clock);
    Instant start = determineStart(parsedRange, snapshots, end);
    List<PricePoint> prices = fetchPrices(assetType, symbol, parsedRange, start, end);
    List<PortfolioAccountingTransaction> accountingTransactions =
        snapshots.stream().map(this::toAccountingTransaction).toList();
    List<TimeValuePoint> series =
        holdingsValueCalculator.calculate(
            prices, quantityTimelineEngine.buildQuantityTimeline(accountingTransactions));
    if (parsedRange.isAll()) {
      long startEpoch = start.getEpochSecond();
      series = series.stream().filter(point -> point.time() >= startEpoch).toList();
    }

    return new AssetHoldingsHistoryResponse(series, markers(snapshots));
  }

  private List<PricePoint> fetchPrices(
      AssetType assetType, String symbol, HoldingsHistoryRange range, Instant start, Instant end) {
    if (!range.isAll()) {
      return marketPriceHistoryPort.getPriceHistory(assetType, symbol, range.value());
    }
    ChartResolution chartResolution = chartResolutionStrategy.resolve(start, end);
    return marketPriceHistoryPort.getPriceHistory(assetType, symbol, chartResolution);
  }

  private Instant determineStart(
      HoldingsHistoryRange range, List<PortfolioTransactionSnapshot> snapshots, Instant end) {
    if (!range.isAll()) {
      return end.minus(Duration.ofDays(range.days()));
    }
    long firstTxEpoch =
        snapshots.stream()
            .map(snapshot -> snapshot.transactionDate().toInstant().getEpochSecond())
            .min(Long::compareTo)
            .orElse(end.getEpochSecond());
    return Instant.ofEpochSecond(alignToUtcDayStart(firstTxEpoch));
  }

  private static long alignToUtcDayStart(long epochSeconds) {
    return epochSeconds - (epochSeconds % 86400L);
  }

  private PortfolioAccountingTransaction toAccountingTransaction(
      PortfolioTransactionSnapshot snapshot) {
    return new PortfolioAccountingTransaction(
        snapshot.transactionDate().toInstant(),
        snapshot.assetSymbol(),
        AssetType.from(snapshot.assetType()),
        snapshot.transactionType(),
        snapshot.quantity(),
        snapshot.pricePerUnit(),
        snapshot.totalValue(),
        snapshot.fee());
  }

  private List<AssetHistoryMarkerResponse> markers(List<PortfolioTransactionSnapshot> snapshots) {
    return snapshots.stream()
        .filter(snapshot -> isBuyOrSell(snapshot.transactionType()))
        .map(
            snapshot ->
                new AssetHistoryMarkerResponse(
                    snapshot.transactionDate().toEpochSecond(),
                    snapshot.transactionType().toUpperCase(),
                    amount(snapshot.quantity()),
                    amount(snapshot.pricePerUnit())))
        .toList();
  }

  private boolean isBuyOrSell(String type) {
    return "BUY".equalsIgnoreCase(type) || "SELL".equalsIgnoreCase(type);
  }

  private BigDecimal amount(BigDecimal value) {
    return value != null ? value : BigDecimal.ZERO;
  }
}
