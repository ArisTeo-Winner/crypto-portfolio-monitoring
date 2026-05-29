package com.mx.cryptomonitor.portfolio.application.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.mx.cryptomonitor.portfolio.application.port.out.PortfolioTransactionSnapshot;
import com.mx.cryptomonitor.portfolio.domain.engine.AcbEngine;
import com.mx.cryptomonitor.portfolio.domain.engine.FifoEngine;
import com.mx.cryptomonitor.portfolio.domain.model.AssetType;
import com.mx.cryptomonitor.portfolio.domain.model.PortfolioAccountingTransaction;
import com.mx.cryptomonitor.portfolio.domain.model.RealizedPnLPoint;

@Service
public class RealizedPnLService {

  private static final int MONEY_SCALE = 2;

  public List<RealizedPnLPoint> calculateAcb(List<PortfolioAccountingTransaction> transactions) {
    Map<String, AcbEngine> engines = new LinkedHashMap<>();
    List<RealizedPnLPoint> points = new ArrayList<>();

    for (PortfolioAccountingTransaction transaction : ordered(transactions)) {
      AcbEngine engine =
          engines.computeIfAbsent(engineKey(transaction), ignored -> new AcbEngine());
      if ("BUY".equalsIgnoreCase(transaction.transactionType())) {
        engine.buy(transaction.quantity(), transaction.price(), transaction.fee());
      } else if ("SELL".equalsIgnoreCase(transaction.transactionType())) {
        points.add(
            new RealizedPnLPoint(
                transaction.time(),
                engine.sell(transaction.quantity(), transaction.price(), transaction.fee())));
      }
    }
    return points;
  }

  public List<RealizedPnLPoint> calculateFifo(List<PortfolioAccountingTransaction> transactions) {
    Map<String, FifoEngine> engines = new LinkedHashMap<>();
    List<RealizedPnLPoint> points = new ArrayList<>();

    for (PortfolioAccountingTransaction transaction : ordered(transactions)) {
      FifoEngine engine =
          engines.computeIfAbsent(engineKey(transaction), ignored -> new FifoEngine());
      if ("BUY".equalsIgnoreCase(transaction.transactionType())) {
        engine.buy(transaction.quantity(), transaction.price());
      } else if ("SELL".equalsIgnoreCase(transaction.transactionType())) {
        points.add(
            new RealizedPnLPoint(
                transaction.time(),
                engine.sell(transaction.quantity(), transaction.price(), transaction.fee())));
      }
    }
    return points;
  }

  public List<RealizedPnLPoint> fromPersistedSnapshots(
      List<PortfolioTransactionSnapshot> snapshots) {
    return snapshots.stream()
        .filter(snapshot -> "SELL".equalsIgnoreCase(snapshot.transactionType()))
        .map(
            snapshot ->
                new RealizedPnLPoint(
                    snapshot.transactionDate().toInstant(),
                    amount(snapshot.realizedPnl()).setScale(MONEY_SCALE, RoundingMode.HALF_UP)))
        .toList();
  }

  public PortfolioAccountingTransaction toAccountingTransaction(
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

  private List<PortfolioAccountingTransaction> ordered(
      List<PortfolioAccountingTransaction> transactions) {
    return transactions.stream()
        .sorted(Comparator.comparing(PortfolioAccountingTransaction::time))
        .toList();
  }

  private String engineKey(PortfolioAccountingTransaction transaction) {
    return transaction.assetType().name() + ":" + transaction.assetSymbol().toUpperCase();
  }

  private BigDecimal amount(BigDecimal value) {
    return value != null ? value : BigDecimal.ZERO;
  }
}
