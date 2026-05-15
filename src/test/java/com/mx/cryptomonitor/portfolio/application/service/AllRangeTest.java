package com.mx.cryptomonitor.portfolio.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
import com.mx.cryptomonitor.portfolio.domain.model.Resolution;

@ExtendWith(MockitoExtension.class)
class AllRangeTest {

  @Mock TransactionHistoryPort transactionHistoryPort;
  @Mock MarketPriceHistoryPort marketPriceHistoryPort;
  @Mock PortfolioAssetUniversePort portfolioAssetUniversePort;

  @InjectMocks GetPortfolioTotalHistoryService service;

  @Test
  void allRange_startAlignedToFirstTransactionDay() {
    UUID userId = UUID.randomUUID();
    LocalDateTime firstTx = LocalDateTime.of(2022, 1, 10, 14, 30);
    PortfolioTransactionSnapshot snapshot = snapshot("BTC", firstTx, "BUY");

    when(transactionHistoryPort.getTransactionsByUser(userId)).thenReturn(List.of(snapshot));
    when(portfolioAssetUniversePort.getAssetsByUser(userId)).thenReturn(List.of());
    when(marketPriceHistoryPort.getPriceHistory(eq(AssetType.CRYPTO), eq("BTC"), any(ChartResolution.class)))
        .thenAnswer(inv -> {
          ChartResolution cr = inv.getArgument(2);
          long expectedStart = firstTx.toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC).getEpochSecond();
          assertThat(cr.start().getEpochSecond()).isEqualTo(expectedStart);
          return dailyPrices(cr.start(), cr.end(), new BigDecimal("45000"));
        });

    PortfolioHistoryResult result = service.getTotalHistory(userId, "all", null);

    assertThat(result.series()).isNotEmpty();
    long firstTxDay = firstTx.toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC).getEpochSecond();
    assertThat(result.series()).allMatch(p -> p.time() >= firstTxDay);
  }

  @Test
  void allRange_noTransactions_returnsEmptySeries() {
    UUID userId = UUID.randomUUID();
    when(transactionHistoryPort.getTransactionsByUser(userId)).thenReturn(List.of());
    when(portfolioAssetUniversePort.getAssetsByUser(userId)).thenReturn(List.of());

    PortfolioHistoryResult result = service.getTotalHistory(userId, "all", null);

    assertThat(result.series()).isEmpty();
    assertThat(result.resolution()).isEqualTo(Resolution.DAILY);
  }

  private PortfolioTransactionSnapshot snapshot(String symbol, LocalDateTime date, String type) {
    return new PortfolioTransactionSnapshot(
        symbol, "CRYPTO", type, null,
        new BigDecimal("0.1"),
        new BigDecimal("4500"),
        new BigDecimal("45000"),
        BigDecimal.ZERO,
        date);
  }

  private List<PricePoint> dailyPrices(Instant start, Instant end, BigDecimal price) {
    List<PricePoint> points = new ArrayList<>();
    long current = start.getEpochSecond();
    long endSec = end.getEpochSecond();
    while (current <= endSec) {
      points.add(new PricePoint(Instant.ofEpochSecond(current), price));
      current += 86400;
    }
    return points;
  }
}
