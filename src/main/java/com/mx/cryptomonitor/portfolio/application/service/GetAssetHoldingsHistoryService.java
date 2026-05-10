package com.mx.cryptomonitor.portfolio.application.service;

import java.math.BigDecimal;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

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
import com.mx.cryptomonitor.portfolio.domain.model.PortfolioAccountingTransaction;
import com.mx.cryptomonitor.portfolio.domain.model.PricePoint;
import com.mx.cryptomonitor.portfolio.domain.model.TimeValuePoint;

@Service
public class GetAssetHoldingsHistoryService implements GetAssetHoldingsHistoryUseCase {

  private final MarketPriceHistoryPort marketPriceHistoryPort;
  private final AssetTransactionHistoryPort assetTransactionHistoryPort;
  private final PortfolioQuantityTimelineEngine quantityTimelineEngine;
  private final HoldingsValueCalculator holdingsValueCalculator;

  public GetAssetHoldingsHistoryService(
      MarketPriceHistoryPort marketPriceHistoryPort,
      AssetTransactionHistoryPort assetTransactionHistoryPort) {
    this.marketPriceHistoryPort = marketPriceHistoryPort;
    this.assetTransactionHistoryPort = assetTransactionHistoryPort;
    this.quantityTimelineEngine = new PortfolioQuantityTimelineEngine();
    this.holdingsValueCalculator = new HoldingsValueCalculator();
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
    List<PricePoint> prices =
        marketPriceHistoryPort.getPriceHistory(assetType, symbol, parsedRange.value());
    List<PortfolioAccountingTransaction> accountingTransactions =
        snapshots.stream().map(this::toAccountingTransaction).toList();
    List<TimeValuePoint> series =
        holdingsValueCalculator.calculate(
            prices, quantityTimelineEngine.buildQuantityTimeline(accountingTransactions));

    return new AssetHoldingsHistoryResponse(series, markers(snapshots));
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

  private List<AssetHistoryMarkerResponse> markers(List<PortfolioTransactionSnapshot> snapshots) {
    return snapshots.stream()
        .filter(snapshot -> isBuyOrSell(snapshot.transactionType()))
        .map(
            snapshot ->
                new AssetHistoryMarkerResponse(
                    snapshot.transactionDate().toEpochSecond(ZoneOffset.UTC),
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
