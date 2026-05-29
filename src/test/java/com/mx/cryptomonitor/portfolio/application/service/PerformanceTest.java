package com.mx.cryptomonitor.portfolio.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.mx.cryptomonitor.portfolio.application.port.out.MarketPriceHistoryPort;
import com.mx.cryptomonitor.portfolio.application.port.out.PortfolioAssetUniversePort;
import com.mx.cryptomonitor.portfolio.application.port.out.PortfolioTransactionSnapshot;
import com.mx.cryptomonitor.portfolio.application.port.out.TransactionHistoryPort;
import com.mx.cryptomonitor.portfolio.domain.model.AssetType;
import com.mx.cryptomonitor.portfolio.domain.model.ChartResolution;
import com.mx.cryptomonitor.portfolio.domain.model.PortfolioHistoryResult;
import com.mx.cryptomonitor.portfolio.domain.model.PricePoint;

@ExtendWith(MockitoExtension.class)
class PerformanceTest {

  @Mock TransactionHistoryPort transactionHistoryPort;
  @Mock MarketPriceHistoryPort marketPriceHistoryPort;
  @Mock PortfolioAssetUniversePort portfolioAssetUniversePort;

  @InjectMocks GetPortfolioTotalHistoryService service;

  @Test
  @Timeout(5)
  void fiveThousandPoints_processedInUnderFiveSeconds() {
    UUID userId = UUID.randomUUID();
    LocalDateTime txDate = LocalDateTime.now(ZoneOffset.UTC).minusDays(5000);
    var snapshot =
        new PortfolioTransactionSnapshot(
            "BTC",
            "CRYPTO",
            "BUY",
            null,
            new BigDecimal("1"),
            new BigDecimal("50000"),
            new BigDecimal("50000"),
            BigDecimal.ZERO,
            txDate);

    when(transactionHistoryPort.getTransactionsByUser(userId)).thenReturn(List.of(snapshot));
    when(portfolioAssetUniversePort.getAssetsByUser(userId)).thenReturn(List.of());
    when(marketPriceHistoryPort.getPriceHistory(
            any(AssetType.class), any(), any(ChartResolution.class)))
        .thenAnswer(
            inv -> {
              ChartResolution cr = inv.getArgument(2);
              return buildDailyPrices(cr.start(), cr.end(), new BigDecimal("50000"));
            });

    PortfolioHistoryResult result = service.getTotalHistory(userId, "all", null);

    assertThat(result.series().size()).isLessThanOrEqualTo(1500);
    assertThat(result.series()).isNotEmpty();
  }

  @Test
  @Timeout(5)
  void tenThousandPoints_multipleAssets_processedInUnderFiveSeconds() {
    UUID userId = UUID.randomUUID();
    List<PortfolioTransactionSnapshot> snapshots =
        List.of(
            txSnapshot("BTC", LocalDateTime.now(ZoneOffset.UTC).minusDays(3500)),
            txSnapshot("ETH", LocalDateTime.now(ZoneOffset.UTC).minusDays(2500)),
            txSnapshot("SOL", LocalDateTime.now(ZoneOffset.UTC).minusDays(1500)));

    when(transactionHistoryPort.getTransactionsByUser(userId)).thenReturn(snapshots);
    when(portfolioAssetUniversePort.getAssetsByUser(userId)).thenReturn(List.of());
    when(marketPriceHistoryPort.getPriceHistory(
            any(AssetType.class), any(), any(ChartResolution.class)))
        .thenAnswer(
            inv -> {
              ChartResolution cr = inv.getArgument(2);
              return buildDailyPrices(cr.start(), cr.end(), new BigDecimal("50000"));
            });

    PortfolioHistoryResult result = service.getTotalHistory(userId, "all", null);

    assertThat(result.series().size()).isLessThanOrEqualTo(1500);
  }

  private PortfolioTransactionSnapshot txSnapshot(String symbol, LocalDateTime date) {
    return new PortfolioTransactionSnapshot(
        symbol,
        "CRYPTO",
        "BUY",
        null,
        new BigDecimal("1"),
        new BigDecimal("50000"),
        new BigDecimal("50000"),
        BigDecimal.ZERO,
        date);
  }

  private List<PricePoint> buildDailyPrices(Instant start, Instant end, BigDecimal price) {
    List<PricePoint> points = new ArrayList<>();
    long current = start.getEpochSecond() - (start.getEpochSecond() % 86400L);
    long endSec = end.getEpochSecond();
    while (current <= endSec) {
      points.add(new PricePoint(Instant.ofEpochSecond(current), price));
      current += 86400;
    }
    return points;
  }
}
