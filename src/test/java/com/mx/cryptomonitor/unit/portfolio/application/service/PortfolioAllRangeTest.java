package com.mx.cryptomonitor.unit.portfolio.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mx.cryptomonitor.portfolio.application.port.out.MarketPriceHistoryPort;
import com.mx.cryptomonitor.portfolio.application.port.out.PortfolioAssetUniversePort;
import com.mx.cryptomonitor.portfolio.application.port.out.PortfolioTransactionSnapshot;
import com.mx.cryptomonitor.portfolio.application.port.out.TransactionHistoryPort;
import com.mx.cryptomonitor.portfolio.application.service.GetPortfolioTotalHistoryService;
import com.mx.cryptomonitor.portfolio.application.service.HoldingsHistoryRange;
import com.mx.cryptomonitor.portfolio.domain.model.AssetType;
import com.mx.cryptomonitor.portfolio.domain.model.ChartResolution;
import com.mx.cryptomonitor.portfolio.domain.model.PortfolioHistoryResult;
import com.mx.cryptomonitor.portfolio.domain.model.PricePoint;
import com.mx.cryptomonitor.portfolio.domain.model.Resolution;
import com.mx.cryptomonitor.portfolio.domain.model.TimeValuePoint;

class PortfolioAllRangeTest {

  private static final long SECONDS_PER_DAY = 86400L;

  private final TransactionHistoryPort transactionHistoryPort = mock(TransactionHistoryPort.class);
  private final MarketPriceHistoryPort marketPriceHistoryPort = mock(MarketPriceHistoryPort.class);
  private final PortfolioAssetUniversePort portfolioAssetUniversePort =
      mock(PortfolioAssetUniversePort.class);
  private final GetPortfolioTotalHistoryService service =
      new GetPortfolioTotalHistoryService(
          transactionHistoryPort, marketPriceHistoryPort, portfolioAssetUniversePort);

  @Test
  void allRangeIsParsedWithDailyResolution() {
    HoldingsHistoryRange range = HoldingsHistoryRange.parse("all");

    assertThat(range.value()).isEqualTo("all");
    assertThat(range.resolution()).isEqualTo(Resolution.DAILY);
    assertThat(range.isAll()).isTrue();
    assertThat(range.stepSeconds()).isEqualTo(SECONDS_PER_DAY);
  }

  @Test
  void allRangeDaysIsSentinelNegativeOne() {
    assertThat(HoldingsHistoryRange.parse("all").days()).isEqualTo(-1);
  }

  @Test
  void invalidRangeStillThrows() {
    assertThatThrownBy(() -> HoldingsHistoryRange.parse("6m"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void seriesStartsAtOrAfterFirstTransactionDay() {
    UUID userId = UUID.randomUUID();
    OffsetDateTime firstTxDate = OffsetDateTime.now(ZoneOffset.UTC).minusDays(400);
    long firstTxDayAligned = alignToUtcDayStart(firstTxDate.toInstant().getEpochSecond());

    when(transactionHistoryPort.getTransactionsByUser(userId))
        .thenReturn(List.of(snapshot("BTC", firstTxDate, new BigDecimal("1"))));
    when(portfolioAssetUniversePort.getAssetsByUser(userId)).thenReturn(List.of());
    // Return prices within the actual range resolved by the service
    when(marketPriceHistoryPort.getPriceHistory(
            eq(AssetType.CRYPTO), eq("BTC"), any(ChartResolution.class)))
        .thenAnswer(
            inv -> {
              ChartResolution cr = inv.getArgument(2);
              long day1 = cr.start().getEpochSecond() - (cr.start().getEpochSecond() % 86400L);
              long day2 = day1 + 86400;
              long day3 = day2 + 86400;
              return List.of(
                  new PricePoint(Instant.ofEpochSecond(day1), new BigDecimal("85000")),
                  new PricePoint(Instant.ofEpochSecond(day2), new BigDecimal("86000")),
                  new PricePoint(Instant.ofEpochSecond(day3), new BigDecimal("87000")));
            });

    PortfolioHistoryResult result = service.getTotalHistory(userId, "all", null);

    assertThat(result.resolution()).isEqualTo(Resolution.DAILY);
    assertThat(result.series()).isNotEmpty();
    assertThat(result.from()).isGreaterThanOrEqualTo(firstTxDayAligned);
    assertThat(result.series().stream().mapToLong(TimeValuePoint::time).min().orElse(-1))
        .isGreaterThanOrEqualTo(firstTxDayAligned);
  }

  @Test
  void seriesEndsAtDayAlignedTimestamp() {
    UUID userId = UUID.randomUUID();
    OffsetDateTime txDate = OffsetDateTime.now(ZoneOffset.UTC).minusDays(2);

    when(transactionHistoryPort.getTransactionsByUser(userId))
        .thenReturn(List.of(snapshot("BTC", txDate, new BigDecimal("1"))));
    when(portfolioAssetUniversePort.getAssetsByUser(userId)).thenReturn(List.of());
    when(marketPriceHistoryPort.getPriceHistory(
            eq(AssetType.CRYPTO), eq("BTC"), any(ChartResolution.class)))
        .thenAnswer(
            inv -> {
              ChartResolution cr = inv.getArgument(2);
              long day1 = cr.start().getEpochSecond() - (cr.start().getEpochSecond() % 86400L);
              long day2 = day1 + 86400;
              return List.of(
                  new PricePoint(Instant.ofEpochSecond(day1), new BigDecimal("90000")),
                  new PricePoint(Instant.ofEpochSecond(day2), new BigDecimal("91000")));
            });

    PortfolioHistoryResult result = service.getTotalHistory(userId, "all", null);

    assertThat(result.to() % SECONDS_PER_DAY).isZero();
  }

  private PortfolioTransactionSnapshot snapshot(
      String symbol, OffsetDateTime date, BigDecimal quantity) {
    return new PortfolioTransactionSnapshot(
        symbol, "CRYPTO", "BUY", null, quantity, quantity, BigDecimal.ONE, BigDecimal.ZERO, date);
  }

  private long alignToUtcDayStart(long epochSeconds) {
    return epochSeconds - (epochSeconds % SECONDS_PER_DAY);
  }
}
