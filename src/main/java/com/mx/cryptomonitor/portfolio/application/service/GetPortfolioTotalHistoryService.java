package com.mx.cryptomonitor.portfolio.application.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
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
import com.mx.cryptomonitor.portfolio.application.port.out.PortfolioAssetUniversePort;
import com.mx.cryptomonitor.portfolio.application.port.out.PortfolioAssetUniversePort.PortfolioAssetReference;
import com.mx.cryptomonitor.portfolio.application.port.out.PortfolioTransactionSnapshot;
import com.mx.cryptomonitor.portfolio.application.port.out.TransactionHistoryPort;
import com.mx.cryptomonitor.portfolio.domain.engine.MwrEngine;
import com.mx.cryptomonitor.portfolio.domain.engine.PortfolioHoldingsAggregationEngine;
import com.mx.cryptomonitor.portfolio.domain.engine.TwrEngine;
import com.mx.cryptomonitor.portfolio.domain.model.AssetType;
import com.mx.cryptomonitor.portfolio.domain.model.ChartResolution;
import com.mx.cryptomonitor.portfolio.domain.model.ChartResolutionStrategy;
import com.mx.cryptomonitor.portfolio.domain.model.HistoricalPriceSeries;
import com.mx.cryptomonitor.portfolio.domain.model.PortfolioAccountingTransaction;
import com.mx.cryptomonitor.portfolio.domain.model.PortfolioAssetHistoryInput;
import com.mx.cryptomonitor.portfolio.domain.model.PortfolioHistoryResult;
import com.mx.cryptomonitor.portfolio.domain.model.PricePoint;
import com.mx.cryptomonitor.portfolio.domain.model.ReturnMetrics;
import com.mx.cryptomonitor.portfolio.domain.model.TimeValuePoint;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class GetPortfolioTotalHistoryService implements GetPortfolioTotalHistoryUseCase {

  private static final int DOWNSAMPLE_THRESHOLD = 1200;
  private static final int DOWNSAMPLE_TARGET = 1000;

  private final TransactionHistoryPort transactionHistoryPort;
  private final MarketPriceHistoryPort marketPriceHistoryPort;
  private final PortfolioAssetUniversePort portfolioAssetUniversePort;
  private final PortfolioHoldingsAggregationEngine aggregationEngine;
  private final ChartResolutionStrategy chartResolutionStrategy;
  private final TwrEngine twrEngine;
  private final MwrEngine mwrEngine;

  public GetPortfolioTotalHistoryService(
      TransactionHistoryPort transactionHistoryPort,
      MarketPriceHistoryPort marketPriceHistoryPort,
      PortfolioAssetUniversePort portfolioAssetUniversePort) {
    this.transactionHistoryPort = transactionHistoryPort;
    this.marketPriceHistoryPort = marketPriceHistoryPort;
    this.portfolioAssetUniversePort = portfolioAssetUniversePort;
    this.aggregationEngine = new PortfolioHoldingsAggregationEngine();
    this.chartResolutionStrategy = new ChartResolutionStrategy();
    this.twrEngine = new TwrEngine();
    this.mwrEngine = new MwrEngine();
  }

  @Override
  public PortfolioHistoryResult getTotalHistory(UUID userId, String range, String assetTypes) {
    long started = System.nanoTime();
    HoldingsHistoryRange parsedRange = HoldingsHistoryRange.parse(range);
    EnumSet<AssetType> requestedTypes = parseAssetTypes(assetTypes);

    List<PortfolioTransactionSnapshot> snapshots =
        filterByAssetTypes(transactionHistoryPort.getTransactionsByUser(userId), requestedTypes);

    // fromSafe descarta tipos sin soporte de precios (ej. BONOS) sin romper el flujo
    Map<AssetIdentity, List<PortfolioTransactionSnapshot>> snapshotsByAsset =
        snapshots.stream()
            .filter(s -> AssetType.fromSafe(s.assetType()).isPresent())
            .collect(
                java.util.stream.Collectors.groupingBy(
                    snapshot ->
                        new AssetIdentity(
                            AssetType.fromSafe(snapshot.assetType()).orElseThrow(),
                            snapshot.assetSymbol()),
                    LinkedHashMap::new,
                    java.util.stream.Collectors.toList()));

    portfolioAssetUniversePort.getAssetsByUser(userId).stream()
        .filter(asset -> requestedTypes == null || requestedTypes.contains(asset.assetType()))
        .map(this::toAssetIdentity)
        .forEach(identity -> snapshotsByAsset.putIfAbsent(identity, List.of()));

    Instant end = Instant.now();
    long todayAligned = alignToUtcDayStart(end.getEpochSecond());

    if (snapshotsByAsset.isEmpty()) {
      logGenerated(userId, parsedRange.value(), null, 0, 0, started);
      return new PortfolioHistoryResult(
          parsedRange.value(), parsedRange.resolution(), todayAligned, todayAligned, List.of());
    }

    Instant start = determineStart(parsedRange, snapshots, end);
    ChartResolution chartResolution = chartResolutionStrategy.resolve(start, end);

    List<PortfolioAssetHistoryInput> assets =
        snapshotsByAsset.entrySet().stream()
            .map(entry -> toAssetInput(entry.getKey(), entry.getValue(), chartResolution))
            .filter(input -> !input.priceSeries().points().isEmpty())
            .sorted(Comparator.comparing(PortfolioAssetHistoryInput::symbol))
            .toList();

    List<TimeValuePoint> series =
        aggregationEngine.aggregate(assets, chartResolution.toAggregationResolution());

    // Remove any points that precede the chart start (safety net for ALL range alignment)
    long startEpoch = start.getEpochSecond();
    series = series.stream().filter(p -> p.time() >= startEpoch).toList();

    series = downsample(series);

    long from = series.isEmpty() ? todayAligned : series.get(0).time();
    long to = series.isEmpty() ? todayAligned : series.get(series.size() - 1).time();

    List<PortfolioAccountingTransaction> allTransactions =
        snapshots.stream().map(this::toAccountingTransaction).toList();
    ReturnMetrics returnMetrics = computeReturnMetrics(series, allTransactions, end);

    logGenerated(
        userId,
        parsedRange.value(),
        chartResolution.providerIntervalCode(),
        assets.size(),
        series.size(),
        started);
    return new PortfolioHistoryResult(
        parsedRange.value(),
        chartResolution.toAggregationResolution(),
        from,
        to,
        series,
        returnMetrics);
  }

  private ReturnMetrics computeReturnMetrics(
      List<TimeValuePoint> series,
      List<PortfolioAccountingTransaction> transactions,
      Instant terminalDate) {
    if (series.isEmpty() || transactions.isEmpty()) {
      return null;
    }

    BigDecimal terminalValue = series.get(series.size() - 1).value();

    BigDecimal totalInvested =
        transactions.stream()
            .filter(tx -> "BUY".equalsIgnoreCase(tx.transactionType()))
            .map(
                tx -> {
                  BigDecimal gross = tx.grossValue() != null ? tx.grossValue() : BigDecimal.ZERO;
                  BigDecimal fee = tx.fee() != null ? tx.fee() : BigDecimal.ZERO;
                  return gross.add(fee);
                })
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    BigDecimal sellProceeds =
        transactions.stream()
            .filter(tx -> "SELL".equalsIgnoreCase(tx.transactionType()))
            .map(
                tx -> {
                  BigDecimal gross = tx.grossValue() != null ? tx.grossValue() : BigDecimal.ZERO;
                  BigDecimal fee = tx.fee() != null ? tx.fee() : BigDecimal.ZERO;
                  return gross.subtract(fee);
                })
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    // absoluteGain = current market value + realized proceeds − total cash deployed
    BigDecimal absoluteGain =
        terminalValue.add(sellProceeds).subtract(totalInvested).setScale(2, RoundingMode.HALF_UP);

    BigDecimal twr = twrEngine.calculate(series, transactions);
    BigDecimal mwr = mwrEngine.calculate(transactions, terminalValue, terminalDate);

    return new ReturnMetrics(
        twr, mwr, absoluteGain, totalInvested.setScale(2, RoundingMode.HALF_UP));
  }

  private Instant determineStart(
      HoldingsHistoryRange range, List<PortfolioTransactionSnapshot> snapshots, Instant end) {
    if (!range.isAll()) {
      return end.minus(Duration.ofDays(range.days()));
    }
    if (snapshots.isEmpty()) {
      return end.minus(Duration.ofDays(365));
    }
    long firstTxEpoch =
        snapshots.stream()
            .map(s -> s.transactionDate().toInstant(ZoneOffset.UTC).getEpochSecond())
            .min(Long::compareTo)
            .orElse(end.getEpochSecond() - Duration.ofDays(365).getSeconds());
    return Instant.ofEpochSecond(alignToUtcDayStart(firstTxEpoch));
  }

  private List<TimeValuePoint> downsample(List<TimeValuePoint> series) {
    if (series.size() <= DOWNSAMPLE_THRESHOLD) {
      return series;
    }
    // step >= 2 guarantees effective reduction; never produces more than ~DOWNSAMPLE_TARGET points
    int step = Math.max(2, series.size() / DOWNSAMPLE_TARGET);
    List<TimeValuePoint> result = new ArrayList<>(DOWNSAMPLE_TARGET + 2);
    result.add(series.get(0));
    for (int i = step; i < series.size() - 1; i += step) {
      result.add(series.get(i));
    }
    result.add(series.get(series.size() - 1));
    return result;
  }

  private static long alignToUtcDayStart(long epochSeconds) {
    return epochSeconds - (epochSeconds % 86400L);
  }

  private PortfolioAssetHistoryInput toAssetInput(
      AssetIdentity identity,
      List<PortfolioTransactionSnapshot> snapshots,
      ChartResolution chartResolution) {
    List<PricePoint> prices =
        marketPriceHistoryPort.getPriceHistory(
            identity.assetType(), identity.symbol(), chartResolution);
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

  private AssetIdentity toAssetIdentity(PortfolioAssetReference asset) {
    return new AssetIdentity(asset.assetType(), asset.symbol());
  }

  private List<PortfolioTransactionSnapshot> filterByAssetTypes(
      List<PortfolioTransactionSnapshot> snapshots, EnumSet<AssetType> requestedTypes) {
    // Siempre excluir tipos sin soporte de precios (ej. BONOS)
    if (requestedTypes == null) {
      return snapshots.stream().filter(s -> AssetType.fromSafe(s.assetType()).isPresent()).toList();
    }
    return snapshots.stream()
        .filter(s -> AssetType.fromSafe(s.assetType()).map(requestedTypes::contains).orElse(false))
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

  private void logGenerated(
      UUID userId, String range, String interval, int assetCount, int points, long started) {
    long durationMs = Duration.ofNanos(System.nanoTime() - started).toMillis();
    log.info(
        "portfolio.history.generated userId={} range={} interval={} points={} assetCount={} durationMs={}",
        userId,
        range,
        interval,
        points,
        assetCount,
        durationMs);
  }

  private record AssetIdentity(AssetType assetType, String symbol) {

    private AssetIdentity {
      symbol = symbol.trim().toUpperCase(Locale.ROOT);
    }
  }
}
