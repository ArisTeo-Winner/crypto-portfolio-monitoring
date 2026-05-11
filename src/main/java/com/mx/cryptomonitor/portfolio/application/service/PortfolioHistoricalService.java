package com.mx.cryptomonitor.portfolio.application.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.mx.cryptomonitor.portfolio.application.dto.response.PortfolioChartPointResponse;
import com.mx.cryptomonitor.portfolio.application.dto.response.PortfolioMarkerResponse;
import com.mx.cryptomonitor.portfolio.application.port.in.PortfolioChartPort;
import com.mx.cryptomonitor.portfolio.application.port.out.PortfolioHistoryStorePort;
import com.mx.cryptomonitor.portfolio.application.port.out.PortfolioTransactionSnapshot;
import com.mx.cryptomonitor.portfolio.application.port.out.TransactionHistoryPort;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PortfolioHistoricalService implements PortfolioChartPort {

  private static final int MONEY_SCALE = 2;
  private static final int MAX_CHART_POINTS = 500;

  private final EquitySnapshotService equitySnapshotService;
  private final TransactionHistoryPort transactionHistoryPort;
  private final RealizedPnLService realizedPnLService;
  private final PortfolioRangeValidator rangeValidator;

  @Override
  public List<PortfolioChartPointResponse> getEquityHistory(UUID userId, String range) {
    int rangeDays = rangeValidator.validate(range);
    List<PortfolioHistoryStorePort.PortfolioHistoryPoint> points =
        equitySnapshotService.getEquityHistory(userId, rangeDays);
    if (points.isEmpty()) {
      equitySnapshotService.recordEquitySnapshot(userId);
      points = equitySnapshotService.getEquityHistory(userId, rangeDays);
    }
    return downsample(points).stream()
        .map(point -> new PortfolioChartPointResponse(point.timestamp().getEpochSecond(), money(point.value())))
        .toList();
  }

  @Override
  public List<PortfolioMarkerResponse> getMarkers(UUID userId, String range) {
    int rangeDays = rangeValidator.validate(range);
    Instant cutoff = Instant.now().minusSeconds((long) rangeDays * 24L * 60L * 60L);
    return transactionHistoryPort.getTransactionsByUser(userId).stream()
        .filter(snapshot -> isBuyOrSell(snapshot.transactionType()))
        .filter(snapshot -> !snapshot.transactionDate().toInstant(ZoneOffset.UTC).isBefore(cutoff))
        .map(
            snapshot ->
                new PortfolioMarkerResponse(
                    snapshot.transactionDate().toInstant(ZoneOffset.UTC).getEpochSecond(),
                    markerPosition(snapshot.transactionType()),
                    markerColor(snapshot.transactionType()),
                    markerShape(snapshot.transactionType()),
                    markerText(snapshot)))
        .toList();
  }

  @Override
  public List<PortfolioChartPointResponse> getRealizedPnl(UUID userId, String range) {
    int rangeDays = rangeValidator.validate(range);
    Instant cutoff = Instant.now().minusSeconds((long) rangeDays * 24L * 60L * 60L);
    return realizedPnLService.fromPersistedSnapshots(transactionHistoryPort.getTransactionsByUser(userId)).stream()
        .filter(point -> !point.time().isBefore(cutoff))
        .map(point -> new PortfolioChartPointResponse(point.time().getEpochSecond(), money(point.value())))
        .toList();
  }

  @Override
  public void recordEquitySnapshot(UUID userId) {
    equitySnapshotService.recordEquitySnapshot(userId);
  }

  @Override
  public void recordEquitySnapshotsForActiveUsers() {
    equitySnapshotService.recordEquitySnapshotsForActiveUsers();
  }

  public List<PortfolioHistoryStorePort.PortfolioHistoryPoint> downsample(
      List<PortfolioHistoryStorePort.PortfolioHistoryPoint> points) {
    if (points.size() <= MAX_CHART_POINTS) {
      return points;
    }
    int step = (int) Math.ceil((double) points.size() / MAX_CHART_POINTS);
    List<PortfolioHistoryStorePort.PortfolioHistoryPoint> sampled = new ArrayList<>();
    for (int index = 0; index < points.size(); index += step) {
      sampled.add(points.get(index));
    }
    PortfolioHistoryStorePort.PortfolioHistoryPoint last = points.get(points.size() - 1);
    if (!sampled.get(sampled.size() - 1).timestamp().equals(last.timestamp())) {
      sampled.add(last);
    }
    return sampled;
  }

  private boolean isBuyOrSell(String transactionType) {
    return "BUY".equalsIgnoreCase(transactionType) || "SELL".equalsIgnoreCase(transactionType);
  }

  private String markerPosition(String transactionType) {
    return "BUY".equalsIgnoreCase(transactionType) ? "belowBar" : "aboveBar";
  }

  private String markerColor(String transactionType) {
    return "BUY".equalsIgnoreCase(transactionType) ? "#22c55e" : "#ef4444";
  }

  private String markerShape(String transactionType) {
    return "BUY".equalsIgnoreCase(transactionType) ? "arrowUp" : "arrowDown";
  }

  private String markerText(PortfolioTransactionSnapshot snapshot) {
    BigDecimal quantity = snapshot.quantity() != null ? snapshot.quantity() : BigDecimal.ZERO;
    String type = snapshot.transactionType().trim().toUpperCase(Locale.ROOT);
    return type
        + " "
        + quantity.stripTrailingZeros().toPlainString()
        + " "
        + snapshot.assetSymbol();
  }

  private BigDecimal money(BigDecimal value) {
    return (value != null ? value : BigDecimal.ZERO).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
  }
}
