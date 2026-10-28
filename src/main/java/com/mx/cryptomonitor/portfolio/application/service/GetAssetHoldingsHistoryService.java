package com.mx.cryptomonitor.portfolio.application.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.mx.cryptomonitor.marketdata.application.port.out.StockSplitPort;
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

  private static final int COST_SCALE = 8;
  private static final StockSplitPort NO_SPLITS = symbol -> List.of();

  private final MarketPriceHistoryPort marketPriceHistoryPort;
  private final AssetTransactionHistoryPort assetTransactionHistoryPort;
  private final StockSplitPort stockSplitPort;
  private final PortfolioQuantityTimelineEngine quantityTimelineEngine;
  private final HoldingsValueCalculator holdingsValueCalculator;
  private final ChartResolutionStrategy chartResolutionStrategy;
  private final Clock clock;

  @Autowired
  public GetAssetHoldingsHistoryService(
      MarketPriceHistoryPort marketPriceHistoryPort,
      AssetTransactionHistoryPort assetTransactionHistoryPort,
      StockSplitPort stockSplitPort) {
    this(marketPriceHistoryPort, assetTransactionHistoryPort, stockSplitPort, Clock.systemUTC());
  }

  public GetAssetHoldingsHistoryService(
      MarketPriceHistoryPort marketPriceHistoryPort,
      AssetTransactionHistoryPort assetTransactionHistoryPort) {
    this(marketPriceHistoryPort, assetTransactionHistoryPort, NO_SPLITS, Clock.systemUTC());
  }

  public GetAssetHoldingsHistoryService(
      MarketPriceHistoryPort marketPriceHistoryPort,
      AssetTransactionHistoryPort assetTransactionHistoryPort,
      Clock clock) {
    this(marketPriceHistoryPort, assetTransactionHistoryPort, NO_SPLITS, clock);
  }

  public GetAssetHoldingsHistoryService(
      MarketPriceHistoryPort marketPriceHistoryPort,
      AssetTransactionHistoryPort assetTransactionHistoryPort,
      StockSplitPort stockSplitPort,
      Clock clock) {
    this.marketPriceHistoryPort = marketPriceHistoryPort;
    this.assetTransactionHistoryPort = assetTransactionHistoryPort;
    this.stockSplitPort = stockSplitPort;
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
    // Un solo simbolo: resolver splitsFor(symbol) una vez y reutilizar en serie y markers.
    List<com.mx.cryptomonitor.marketdata.application.port.out.StockSplitData> splits =
        SplitFactors.splitsOf(stockSplitPort, symbol);
    List<PortfolioAccountingTransaction> accountingTransactions =
        snapshots.stream().map(snapshot -> toAccountingTransaction(snapshot, splits)).toList();
    List<TimeValuePoint> series =
        holdingsValueCalculator.calculate(
            prices, quantityTimelineEngine.buildQuantityTimeline(accountingTransactions));
    if (parsedRange.isAll()) {
      long startEpoch = start.getEpochSecond();
      series = series.stream().filter(point -> point.time() >= startEpoch).toList();
    }

    return new AssetHoldingsHistoryResponse(series, markers(snapshots, splits));
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
      return range.startFrom(end);
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
      PortfolioTransactionSnapshot snapshot,
      List<com.mx.cryptomonitor.marketdata.application.port.out.StockSplitData> splits) {
    // ADR-0011 Fase 1b: la serie valua cantidad_timeline x precio_de_mercado, y el feed historico
    // ya viene ajustado (adjusted=true). Por eso la cantidad debe ir en terminos post-split; el
    // precio unitario se divide para dejar price*qty invariante.
    BigDecimal factor = SplitFactors.factorFor(splits, snapshot.transactionDate());
    return new PortfolioAccountingTransaction(
        snapshot.transactionDate().toInstant(),
        snapshot.assetSymbol(),
        AssetType.from(snapshot.assetType()),
        snapshot.transactionType(),
        SplitFactors.adjustQuantity(snapshot.quantity(), factor),
        SplitFactors.adjustPrice(snapshot.pricePerUnit(), factor, COST_SCALE),
        snapshot.totalValue(),
        snapshot.fee());
  }

  private List<AssetHistoryMarkerResponse> markers(
      List<PortfolioTransactionSnapshot> snapshots,
      List<com.mx.cryptomonitor.marketdata.application.port.out.StockSplitData> splits) {
    return snapshots.stream()
        .filter(snapshot -> isBuyOrSell(snapshot.transactionType()))
        .map(
            snapshot -> {
              // El marker debe caer sobre la linea ya ajustada: cantidad x factor, precio / factor.
              BigDecimal factor = SplitFactors.factorFor(splits, snapshot.transactionDate());
              return new AssetHistoryMarkerResponse(
                  snapshot.transactionDate().toEpochSecond(),
                  snapshot.transactionType().toUpperCase(),
                  amount(SplitFactors.adjustQuantity(snapshot.quantity(), factor)),
                  amount(SplitFactors.adjustPrice(snapshot.pricePerUnit(), factor, COST_SCALE)));
            })
        .toList();
  }

  private boolean isBuyOrSell(String type) {
    return "BUY".equalsIgnoreCase(type) || "SELL".equalsIgnoreCase(type);
  }

  private BigDecimal amount(BigDecimal value) {
    return value != null ? value : BigDecimal.ZERO;
  }
}
