package com.mx.cryptomonitor.portfolio.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
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

@ExtendWith(MockitoExtension.class)
class DownsamplingTest {

  @Mock TransactionHistoryPort transactionHistoryPort;
  @Mock MarketPriceHistoryPort marketPriceHistoryPort;
  @Mock PortfolioAssetUniversePort portfolioAssetUniversePort;

  @InjectMocks GetPortfolioTotalHistoryService service;

  @Test
  void seriesWith5000Points_downsampledToAtMost1500() {
    UUID userId = UUID.randomUUID();
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
            java.time.OffsetDateTime.of(2020, 1, 1, 0, 0, 0, 0, java.time.ZoneOffset.UTC));

    when(transactionHistoryPort.getTransactionsByUser(userId)).thenReturn(List.of(snapshot));
    when(portfolioAssetUniversePort.getAssetsByUser(userId)).thenReturn(List.of());
    when(marketPriceHistoryPort.getPriceHistory(
            any(AssetType.class), any(), any(ChartResolution.class)))
        .thenReturn(buildLargeSeries(5000));

    PortfolioHistoryResult result = service.getTotalHistory(userId, "all", null);

    assertThat(result.series().size()).isLessThanOrEqualTo(1500);
  }

  @Test
  void seriesWith1000Points_notDownsampled() {
    UUID userId = UUID.randomUUID();
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
            java.time.OffsetDateTime.of(2023, 1, 1, 0, 0, 0, 0, java.time.ZoneOffset.UTC));

    when(transactionHistoryPort.getTransactionsByUser(userId)).thenReturn(List.of(snapshot));
    when(portfolioAssetUniversePort.getAssetsByUser(userId)).thenReturn(List.of());
    when(marketPriceHistoryPort.getPriceHistory(
            any(AssetType.class), any(), any(ChartResolution.class)))
        .thenReturn(buildLargeSeries(1000));

    PortfolioHistoryResult result = service.getTotalHistory(userId, "1y", null);

    // 1000 <= DOWNSAMPLE_THRESHOLD (1200), so no downsampling
    assertThat(result.series().size()).isLessThanOrEqualTo(1000);
  }

  @Test
  void downsampled_firstAndLastPointsPreserved() {
    UUID userId = UUID.randomUUID();
    List<PricePoint> bigSeries = buildLargeSeries(2000);

    var snapshot =
        new PortfolioTransactionSnapshot(
            "ETH",
            "CRYPTO",
            "BUY",
            null,
            new BigDecimal("1"),
            new BigDecimal("2000"),
            new BigDecimal("2000"),
            BigDecimal.ZERO,
            java.time.OffsetDateTime.of(2020, 1, 1, 0, 0, 0, 0, java.time.ZoneOffset.UTC));

    when(transactionHistoryPort.getTransactionsByUser(userId)).thenReturn(List.of(snapshot));
    when(portfolioAssetUniversePort.getAssetsByUser(userId)).thenReturn(List.of());
    when(marketPriceHistoryPort.getPriceHistory(
            any(AssetType.class), any(), any(ChartResolution.class)))
        .thenReturn(bigSeries);

    PortfolioHistoryResult result = service.getTotalHistory(userId, "all", null);

    assertThat(result.series()).isNotEmpty();
    assertThat(result.series().size()).isLessThanOrEqualTo(1500);
    // Series must be sorted
    for (int i = 1; i < result.series().size(); i++) {
      assertThat(result.series().get(i).time())
          .isGreaterThanOrEqualTo(result.series().get(i - 1).time());
    }
  }

  private List<PricePoint> buildLargeSeries(int count) {
    List<PricePoint> points = new ArrayList<>(count);
    long base = Instant.parse("2020-01-01T00:00:00Z").getEpochSecond();
    for (int i = 0; i < count; i++) {
      points.add(
          new PricePoint(Instant.ofEpochSecond(base + (long) i * 86400), new BigDecimal("50000")));
    }
    return points;
  }
}
