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
import com.mx.cryptomonitor.portfolio.domain.model.TimeValuePoint;

@ExtendWith(MockitoExtension.class)
class NoContractBreakTest {

  @Mock TransactionHistoryPort transactionHistoryPort;
  @Mock MarketPriceHistoryPort marketPriceHistoryPort;
  @Mock PortfolioAssetUniversePort portfolioAssetUniversePort;

  @InjectMocks GetPortfolioTotalHistoryService service;

  @Test
  void seriesPointsHaveTimeAndValue() {
    UUID userId = UUID.randomUUID();
    // Transaction 60 days ago so it's within any range we query
    LocalDateTime txDate = LocalDateTime.now(ZoneOffset.UTC).minusDays(60);
    var snapshot =
        new PortfolioTransactionSnapshot(
            "BTC",
            "CRYPTO",
            "BUY",
            null,
            new BigDecimal("0.5"),
            new BigDecimal("22500"),
            new BigDecimal("45000"),
            BigDecimal.ZERO,
            txDate);

    when(transactionHistoryPort.getTransactionsByUser(userId)).thenReturn(List.of(snapshot));
    when(portfolioAssetUniversePort.getAssetsByUser(userId)).thenReturn(List.of());
    // Return prices in the recent 30-day window the service will request
    when(marketPriceHistoryPort.getPriceHistory(
            any(AssetType.class), any(), any(ChartResolution.class)))
        .thenAnswer(
            inv -> {
              ChartResolution cr = inv.getArgument(2);
              return buildPricesInRange(cr.start(), cr.end(), new BigDecimal("45000"));
            });

    PortfolioHistoryResult result = service.getTotalHistory(userId, "30d", null);

    assertThat(result.series()).isNotEmpty();
    for (TimeValuePoint point : result.series()) {
      assertThat(point.time()).isPositive();
      assertThat(point.value()).isNotNull();
    }
  }

  @Test
  void seriesNeverExceeds1500Points() {
    UUID userId = UUID.randomUUID();
    LocalDateTime txDate = LocalDateTime.of(2020, 1, 1, 0, 0);
    var snapshot =
        new PortfolioTransactionSnapshot(
            "ETH",
            "CRYPTO",
            "BUY",
            null,
            new BigDecimal("2"),
            new BigDecimal("4000"),
            new BigDecimal("2000"),
            BigDecimal.ZERO,
            txDate);

    when(transactionHistoryPort.getTransactionsByUser(userId)).thenReturn(List.of(snapshot));
    when(portfolioAssetUniversePort.getAssetsByUser(userId)).thenReturn(List.of());
    when(marketPriceHistoryPort.getPriceHistory(
            any(AssetType.class), any(), any(ChartResolution.class)))
        .thenAnswer(
            inv -> {
              ChartResolution cr = inv.getArgument(2);
              return buildPricesInRange(cr.start(), cr.end(), new BigDecimal("2000"));
            });

    PortfolioHistoryResult result = service.getTotalHistory(userId, "all", null);

    assertThat(result.series().size()).isLessThanOrEqualTo(1500);
  }

  @Test
  void rangeAndResolutionFieldsPresent() {
    UUID userId = UUID.randomUUID();
    when(transactionHistoryPort.getTransactionsByUser(userId)).thenReturn(List.of());
    when(portfolioAssetUniversePort.getAssetsByUser(userId)).thenReturn(List.of());

    for (String range : List.of("24h", "7d", "30d", "90d", "180d", "1y", "all")) {
      PortfolioHistoryResult result = service.getTotalHistory(userId, range, null);
      assertThat(result.rangeLabel()).as("range=%s", range).isNotBlank();
      assertThat(result.resolution()).as("range=%s", range).isNotNull();
    }
  }

  @Test
  void seriesIsSortedByTime() {
    UUID userId = UUID.randomUUID();
    LocalDateTime txDate = LocalDateTime.now(ZoneOffset.UTC).minusDays(400);
    var snapshot =
        new PortfolioTransactionSnapshot(
            "BTC",
            "CRYPTO",
            "BUY",
            null,
            new BigDecimal("1"),
            new BigDecimal("45000"),
            new BigDecimal("45000"),
            BigDecimal.ZERO,
            txDate);

    when(transactionHistoryPort.getTransactionsByUser(userId)).thenReturn(List.of(snapshot));
    when(portfolioAssetUniversePort.getAssetsByUser(userId)).thenReturn(List.of());
    when(marketPriceHistoryPort.getPriceHistory(
            any(AssetType.class), any(), any(ChartResolution.class)))
        .thenAnswer(
            inv -> {
              ChartResolution cr = inv.getArgument(2);
              return buildPricesInRange(cr.start(), cr.end(), new BigDecimal("45000"));
            });

    PortfolioHistoryResult result = service.getTotalHistory(userId, "1y", null);

    for (int i = 1; i < result.series().size(); i++) {
      assertThat(result.series().get(i).time())
          .isGreaterThanOrEqualTo(result.series().get(i - 1).time());
    }
  }

  // Returns one daily price point for each day in [start, end]
  private List<PricePoint> buildPricesInRange(Instant start, Instant end, BigDecimal price) {
    List<PricePoint> points = new ArrayList<>();
    long current = start.getEpochSecond() - (start.getEpochSecond() % 86400L); // align to day
    long endSec = end.getEpochSecond();
    while (current <= endSec) {
      points.add(new PricePoint(Instant.ofEpochSecond(current), price));
      current += 86400;
    }
    return points;
  }
}
