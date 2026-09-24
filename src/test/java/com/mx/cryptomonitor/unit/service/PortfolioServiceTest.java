package com.mx.cryptomonitor.unit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import com.mx.cryptomonitor.asset.application.port.in.AssetCatalogQueryPort;
import com.mx.cryptomonitor.marketdata.application.port.out.AssetPricePort;
import com.mx.cryptomonitor.marketdata.application.port.out.CryptoHistoricalPricePoint;
import com.mx.cryptomonitor.marketdata.application.port.out.CryptoHistoricalPricePort;
import com.mx.cryptomonitor.marketdata.application.port.out.CryptoHistoricalPriceSeries;
import com.mx.cryptomonitor.marketdata.application.port.out.FxRatePort;
import com.mx.cryptomonitor.marketdata.application.port.out.MarketDataProvider;
import com.mx.cryptomonitor.portfolio.application.dto.response.PortfolioHoldingsPerformanceResponse;
import com.mx.cryptomonitor.portfolio.application.port.out.PortfolioTransactionSnapshot;
import com.mx.cryptomonitor.portfolio.application.port.out.TransactionHistoryPort;
import com.mx.cryptomonitor.portfolio.application.service.PortfolioService;
import com.mx.cryptomonitor.portfolio.domain.model.PortfolioEntry;
import com.mx.cryptomonitor.portfolio.domain.repository.PortfolioEntryRepository;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceTest {

  @Mock private PortfolioEntryRepository portfolioEntryRepository;
  @Mock private MarketDataProvider marketDataProvider;
  @Mock private AssetPricePort assetPricePort;
  @Mock private CryptoHistoricalPricePort cryptoHistoricalPricePort;
  @Mock private AssetCatalogQueryPort assetCatalogQueryPort;
  @Mock private TransactionHistoryPort transactionHistoryPort;
  @Mock private FxRatePort fxRatePort;

  @InjectMocks private PortfolioService portfolioService;

  @Test
  void shouldReturnPortfolioEntryByUserAndSymbol() {
    UUID userId = UUID.randomUUID();
    PortfolioEntry entry =
        PortfolioEntry.builder()
            .assetSymbol("ETH")
            .assetType("CRYPTO")
            .totalQuantity(new BigDecimal("1.0"))
            .totalInvested(new BigDecimal("2500.00"))
            .build();
    when(portfolioEntryRepository.findByUserIdAndAssetSymbol(userId, "ETH"))
        .thenReturn(Optional.of(entry));

    Optional<PortfolioEntry> result =
        portfolioService.getPortfolioEntryByUserAndSymbol(userId, "ETH");

    assertThat(result).contains(entry);
    verify(portfolioEntryRepository).findByUserIdAndAssetSymbol(userId, "ETH");
  }

  @Test
  void shouldRemoveStalePortfolioEntriesWithoutTransactions() {
    UUID userId = UUID.randomUUID();
    PortfolioEntry staleEntry =
        PortfolioEntry.builder()
            .portfolioEntryId(UUID.randomUUID())
            .userId(userId)
            .assetSymbol("SOL")
            .assetType("CRYPTO")
            .totalQuantity(new BigDecimal("7"))
            .totalInvested(new BigDecimal("624.65"))
            .build();

    when(transactionHistoryPort.getTransactionsByUser(userId)).thenReturn(List.of());
    when(portfolioEntryRepository.findByUserId(userId)).thenReturn(List.of(staleEntry));

    portfolioService.reconcilePortfolio(userId);

    verify(portfolioEntryRepository).deleteAllInBatch(List.of(staleEntry));
  }

  @Test
  void shouldNotPersistProjectionWhenPortfolioStateIsAlreadyUpToDate() {
    UUID userId = UUID.randomUUID();
    PortfolioEntry entry =
        PortfolioEntry.builder()
            .portfolioEntryId(UUID.randomUUID())
            .userId(userId)
            .assetSymbol("ETH")
            .assetType("CRYPTO")
            .totalQuantity(new BigDecimal("1.0"))
            .totalInvested(new BigDecimal("2500.00"))
            .averagePricePerUnit(new BigDecimal("2500.00000000"))
            .lastTransactionPrice(new BigDecimal("2500.00"))
            .currentValue(new BigDecimal("2500.00"))
            .totalProfitLoss(BigDecimal.ZERO)
            .updatedAt(LocalDateTime.of(2026, 3, 26, 10, 0))
            .build();
    when(transactionHistoryPort.getTransactionsByUser(userId))
        .thenReturn(
            List.of(
                new PortfolioTransactionSnapshot(
                    "ETH",
                    "CRYPTO",
                    "BUY",
                    null,
                    BigDecimal.ONE,
                    new BigDecimal("2500.00"),
                    new BigDecimal("2500.00"),
                    BigDecimal.ZERO,
                    OffsetDateTime.of(2026, 3, 26, 10, 0, 0, 0, ZoneOffset.UTC))));
    when(portfolioEntryRepository.findByUserId(userId)).thenReturn(List.of(entry));
    when(assetPricePort.getCryptoPriceAmount("ETH"))
        .thenReturn(reactor.core.publisher.Mono.just(new BigDecimal("2500.00")));

    portfolioService.reconcilePortfolio(userId);

    verify(portfolioEntryRepository, never()).saveAll(anyList());
    verify(portfolioEntryRepository, never()).deleteAllInBatch(anyList());
  }

  @Test
  void shouldRetryReconcileWhenOptimisticLockOccursDuringProjectionUpdate() {
    UUID userId = UUID.randomUUID();
    PortfolioEntry staleEntry =
        PortfolioEntry.builder()
            .portfolioEntryId(UUID.randomUUID())
            .userId(userId)
            .assetSymbol("ETH")
            .assetType("CRYPTO")
            .totalQuantity(new BigDecimal("1.0"))
            .totalInvested(new BigDecimal("2400.00"))
            .averagePricePerUnit(new BigDecimal("2400.00000000"))
            .lastTransactionPrice(new BigDecimal("2400.00"))
            .currentValue(new BigDecimal("2400.00"))
            .totalProfitLoss(BigDecimal.ZERO)
            .updatedAt(LocalDateTime.of(2026, 3, 26, 9, 0))
            .build();
    PortfolioEntry freshEntry =
        PortfolioEntry.builder()
            .portfolioEntryId(staleEntry.getPortfolioEntryId())
            .userId(userId)
            .assetSymbol("ETH")
            .assetType("CRYPTO")
            .totalQuantity(new BigDecimal("1.0"))
            .totalInvested(new BigDecimal("2500.00"))
            .averagePricePerUnit(new BigDecimal("2500.00000000"))
            .lastTransactionPrice(new BigDecimal("2500.00"))
            .currentValue(new BigDecimal("2500.00"))
            .totalProfitLoss(BigDecimal.ZERO)
            .updatedAt(LocalDateTime.of(2026, 3, 26, 10, 0))
            .build();
    when(transactionHistoryPort.getTransactionsByUser(userId))
        .thenReturn(
            List.of(
                new PortfolioTransactionSnapshot(
                    "ETH",
                    "CRYPTO",
                    "BUY",
                    null,
                    BigDecimal.ONE,
                    new BigDecimal("2500.00"),
                    new BigDecimal("2500.00"),
                    BigDecimal.ZERO,
                    OffsetDateTime.of(2026, 3, 26, 10, 0, 0, 0, ZoneOffset.UTC))));
    when(portfolioEntryRepository.findByUserId(userId))
        .thenReturn(List.of(staleEntry))
        .thenReturn(List.of(freshEntry))
        .thenReturn(List.of(freshEntry));
    when(assetPricePort.getCryptoPriceAmount("ETH"))
        .thenReturn(reactor.core.publisher.Mono.just(new BigDecimal("2500.00")));
    when(portfolioEntryRepository.saveAll(anyList()))
        .thenThrow(
            new ObjectOptimisticLockingFailureException(
                PortfolioEntry.class, staleEntry.getPortfolioEntryId()));

    portfolioService.reconcilePortfolio(userId);
    List<PortfolioEntry> result = portfolioService.getPortfolioEntriesByUser(userId);

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().getTotalInvested()).isEqualByComparingTo("2500.00");
    verify(portfolioEntryRepository).saveAll(anyList());
    verify(portfolioEntryRepository, never()).deleteAllInBatch(anyList());
  }

  @Test
  void shouldReturnPersistedPortfolioEntriesWhenOptimisticLockPersistsAcrossRetries() {
    UUID userId = UUID.randomUUID();
    UUID portfolioEntryId = UUID.randomUUID();
    PortfolioEntry staleEntry =
        PortfolioEntry.builder()
            .portfolioEntryId(portfolioEntryId)
            .userId(userId)
            .assetSymbol("HYPE")
            .assetType("CRYPTO")
            .totalQuantity(new BigDecimal("1.0"))
            .totalInvested(new BigDecimal("13.40"))
            .averagePricePerUnit(new BigDecimal("13.40000000"))
            .lastTransactionPrice(new BigDecimal("38.34"))
            .currentValue(new BigDecimal("38.34"))
            .totalProfitLoss(new BigDecimal("24.94"))
            .updatedAt(LocalDateTime.of(2026, 4, 8, 18, 0))
            .build();
    PortfolioEntry staleEntrySecondAttempt =
        PortfolioEntry.builder()
            .portfolioEntryId(portfolioEntryId)
            .userId(userId)
            .assetSymbol("HYPE")
            .assetType("CRYPTO")
            .totalQuantity(new BigDecimal("1.0"))
            .totalInvested(new BigDecimal("13.40"))
            .averagePricePerUnit(new BigDecimal("13.40000000"))
            .lastTransactionPrice(new BigDecimal("38.34"))
            .currentValue(new BigDecimal("38.34"))
            .totalProfitLoss(new BigDecimal("24.94"))
            .updatedAt(LocalDateTime.of(2026, 4, 8, 18, 0))
            .build();
    PortfolioEntry persistedEntry =
        PortfolioEntry.builder()
            .portfolioEntryId(portfolioEntryId)
            .userId(userId)
            .assetSymbol("HYPE")
            .assetType("CRYPTO")
            .totalQuantity(new BigDecimal("0.75"))
            .totalInvested(new BigDecimal("28.00"))
            .averagePricePerUnit(new BigDecimal("37.33333333"))
            .lastTransactionPrice(new BigDecimal("37.33"))
            .currentValue(new BigDecimal("28.00"))
            .totalProfitLoss(BigDecimal.ZERO)
            .updatedAt(LocalDateTime.of(2026, 4, 8, 18, 1))
            .build();

    when(transactionHistoryPort.getTransactionsByUser(userId))
        .thenReturn(
            List.of(
                new PortfolioTransactionSnapshot(
                    "HYPE",
                    "CRYPTO",
                    "BUY",
                    null,
                    BigDecimal.ONE,
                    new BigDecimal("13.40"),
                    new BigDecimal("13.40"),
                    BigDecimal.ZERO,
                    OffsetDateTime.of(2026, 4, 8, 18, 0, 0, 0, ZoneOffset.UTC))));
    when(portfolioEntryRepository.findByUserId(userId))
        .thenReturn(List.of(staleEntry))
        .thenReturn(List.of(staleEntrySecondAttempt))
        .thenReturn(List.of(persistedEntry))
        .thenReturn(List.of(persistedEntry));
    when(assetPricePort.getCryptoPriceAmount("HYPE"))
        .thenReturn(reactor.core.publisher.Mono.just(new BigDecimal("38.34")));
    when(portfolioEntryRepository.saveAll(anyList()))
        .thenThrow(
            new ObjectOptimisticLockingFailureException(
                PortfolioEntry.class, staleEntry.getPortfolioEntryId()));

    portfolioService.reconcilePortfolio(userId);
    List<PortfolioEntry> result = portfolioService.getPortfolioEntriesByUser(userId);

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().getAssetSymbol()).isEqualTo("HYPE");
    assertThat(result.getFirst().getTotalQuantity()).isEqualByComparingTo("0.75");
    assertThat(result.getFirst().getCurrentValue()).isEqualByComparingTo("28.00");
    assertThat(result.getFirst().getUpdatedAt()).isEqualTo(LocalDateTime.of(2026, 4, 8, 18, 1));
    verify(portfolioEntryRepository, times(4)).findByUserId(userId);
    verify(portfolioEntryRepository, times(2)).saveAll(anyList());
  }

  @Test
  void shouldReturnCurrentCryptoPrice() {
    when(assetPricePort.getCryptoPriceAmount("ETH"))
        .thenReturn(reactor.core.publisher.Mono.just(new BigDecimal("2500.00")));

    Optional<BigDecimal> result = portfolioService.getCurrentCryptoPrice("ETH");

    assertThat(result).contains(new BigDecimal("2500.00"));
    verify(assetPricePort).getCryptoPriceAmount("ETH");
  }

  @Test
  void shouldReturnCurrentTimestamp() {
    assertThat(portfolioService.now()).isNotNull();
  }

  @Test
  void shouldCalculateHoldingsPerformanceForHypeUsingAverageCostMethod() {
    UUID userId = UUID.randomUUID();
    when(transactionHistoryPort.getTransactionsByUser(userId))
        .thenReturn(
            List.of(
                new PortfolioTransactionSnapshot(
                    "HYPE",
                    "CRYPTO",
                    "BUY",
                    null,
                    new BigDecimal("100"),
                    new BigDecimal("1000.00"),
                    new BigDecimal("10.00"),
                    BigDecimal.ZERO,
                    OffsetDateTime.of(2025, 3, 3, 10, 0, 0, 0, ZoneOffset.UTC)),
                new PortfolioTransactionSnapshot(
                    "HYPE",
                    "CRYPTO",
                    "BUY",
                    null,
                    new BigDecimal("50"),
                    new BigDecimal("500.00"),
                    new BigDecimal("10.00"),
                    BigDecimal.ZERO,
                    OffsetDateTime.of(2025, 3, 10, 10, 0, 0, 0, ZoneOffset.UTC)),
                new PortfolioTransactionSnapshot(
                    "HYPE",
                    "CRYPTO",
                    "BUY",
                    null,
                    new BigDecimal("30"),
                    new BigDecimal("691.38"),
                    new BigDecimal("23.046"),
                    BigDecimal.ZERO,
                    OffsetDateTime.of(2025, 3, 17, 10, 0, 0, 0, ZoneOffset.UTC)),
                new PortfolioTransactionSnapshot(
                    "HYPE",
                    "CRYPTO",
                    "SELL",
                    null,
                    new BigDecimal("40"),
                    new BigDecimal("800.00"),
                    new BigDecimal("20.00"),
                    BigDecimal.ZERO,
                    OffsetDateTime.of(2025, 3, 24, 10, 0, 0, 0, ZoneOffset.UTC)),
                new PortfolioTransactionSnapshot(
                    "HYPE",
                    "CRYPTO",
                    "SELL",
                    null,
                    new BigDecimal("50"),
                    new BigDecimal("1750.00"),
                    new BigDecimal("35.00"),
                    BigDecimal.ZERO,
                    OffsetDateTime.of(2025, 3, 31, 10, 0, 0, 0, ZoneOffset.UTC))));
    when(assetPricePort.getCryptoPriceAmount("HYPE"))
        .thenReturn(reactor.core.publisher.Mono.just(new BigDecimal("60.54244444")));
    when(assetCatalogQueryPort.findAssetIdBySymbol("HYPE")).thenReturn(Optional.empty());

    PortfolioHoldingsPerformanceResponse response =
        portfolioService.getHoldingsPerformanceByPortfolioId(userId, "HYPE", "ALL");

    assertThat(response.series()).isNotEmpty();
    assertThat(response.isProfit()).isTrue();
    assertThat(response.costBasis()).isEqualByComparingTo("2191.38");
    assertThat(response.allTimeProfit()).isEqualByComparingTo("5807.44");
    assertThat(response.allTimeProfitPercent()).isEqualByComparingTo("265.01");
    assertThat(response.firstTransactionDate()).isEqualTo(LocalDate.of(2025, 3, 3));
  }

  @Test
  void holdingsPerformanceConvertsMxnCostToUsdBaseUsingFxRate() {
    UUID userId = UUID.randomUUID();
    when(transactionHistoryPort.getTransactionsByUser(userId))
        .thenReturn(
            List.of(
                new PortfolioTransactionSnapshot(
                    "MELIX",
                    "CRYPTO",
                    "BUY",
                    null,
                    new BigDecimal("1"),
                    new BigDecimal("31562.38"),
                    new BigDecimal("31562.38"),
                    new BigDecimal("78.90"),
                    BigDecimal.ZERO,
                    OffsetDateTime.of(2026, 9, 17, 10, 0, 0, 0, ZoneOffset.UTC),
                    "MXN")));
    when(fxRatePort.usdMxnRate()).thenReturn(Optional.of(new BigDecimal("20")));
    when(assetPricePort.getCryptoPriceAmount("MELIX"))
        .thenReturn(reactor.core.publisher.Mono.just(new BigDecimal("2000.00")));
    when(assetCatalogQueryPort.findAssetIdBySymbol("MELIX")).thenReturn(Optional.empty());

    PortfolioHoldingsPerformanceResponse response =
        portfolioService.getHoldingsPerformanceByPortfolioId(userId, "MELIX", "ALL");

    // Costo MXN (31562.38 + 78.90 = 31641.28) normalizado a USD (/20) = 1582.06, NO el crudo en
    // pesos. Antes de ADR-0006 el costo MXN se comparaba contra precio USD -> P&L falso.
    assertThat(response.costBasis()).isEqualByComparingTo("1582.06");
  }

  @Test
  void reconcileConvertsMxnCostToUsdBaseInPortfolioEntry() {
    UUID userId = UUID.randomUUID();
    when(transactionHistoryPort.getTransactionsByUser(userId))
        .thenReturn(
            List.of(
                new PortfolioTransactionSnapshot(
                    "MELIX",
                    "CRYPTO",
                    "BUY",
                    null,
                    BigDecimal.ONE,
                    new BigDecimal("31562.38"),
                    new BigDecimal("31562.38"),
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    OffsetDateTime.of(2026, 9, 17, 10, 0, 0, 0, ZoneOffset.UTC),
                    "MXN")));
    when(portfolioEntryRepository.findByUserId(userId)).thenReturn(List.of());
    when(fxRatePort.usdMxnRate()).thenReturn(Optional.of(new BigDecimal("17.5147")));
    when(assetPricePort.getCryptoPriceAmount("MELIX"))
        .thenReturn(reactor.core.publisher.Mono.just(new BigDecimal("1801.22")));

    PortfolioEntry[] saved = new PortfolioEntry[1];
    when(portfolioEntryRepository.saveAll(anyList()))
        .thenAnswer(
            inv -> {
              List<PortfolioEntry> list = inv.getArgument(0);
              saved[0] = list.get(0);
              return list;
            });

    portfolioService.reconcilePortfolio(userId);

    // Costo MXN 31562.38 normalizado a USD (/17.5147) = 1802.05, NO el crudo en pesos.
    assertThat(saved[0].getTotalInvested()).isEqualByComparingTo("1802.05084872");
    // P&L ~ breakeven (1801.22 - 1802.05), NO el falso -29761.
    assertThat(saved[0].getTotalProfitLoss()).isEqualByComparingTo("-0.83");
  }

  @Test
  void shouldUseCoinGeckoHistoricalSeriesForCryptoHoldingsWhenAssetMappingExists() {
    UUID userId = UUID.randomUUID();
    when(transactionHistoryPort.getTransactionsByUser(userId))
        .thenReturn(
            List.of(
                new PortfolioTransactionSnapshot(
                    "BTC",
                    "CRYPTO",
                    "BUY",
                    null,
                    new BigDecimal("2"),
                    new BigDecimal("20.00"),
                    new BigDecimal("10.00"),
                    BigDecimal.ZERO,
                    OffsetDateTime.of(2025, 4, 7, 0, 0, 0, 0, ZoneOffset.UTC))));
    when(assetCatalogQueryPort.findAssetIdBySymbol("BTC")).thenReturn(Optional.of("bitcoin"));
    when(cryptoHistoricalPricePort.getHistoricalUsdPrices(
            org.mockito.ArgumentMatchers.eq("bitcoin"),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()))
        .thenReturn(
            reactor.core.publisher.Mono.just(
                new CryptoHistoricalPriceSeries(
                    "bitcoin",
                    "usd",
                    List.of(
                        new CryptoHistoricalPricePoint(
                            Instant.parse("2025-04-07T00:00:00Z"), new BigDecimal("10.00")),
                        new CryptoHistoricalPricePoint(
                            Instant.parse("2025-04-08T00:00:00Z"), new BigDecimal("12.00"))))));
    when(assetPricePort.getCryptoPriceAmount("BTC"))
        .thenReturn(reactor.core.publisher.Mono.just(new BigDecimal("13.00")));

    PortfolioHoldingsPerformanceResponse response =
        portfolioService.getHoldingsPerformanceByPortfolioId(userId, "BTC", "ALL");

    assertThat(response.series()).isNotEmpty();
    assertThat(response.series().getFirst().value()).isEqualByComparingTo("20.00");
    assertThat(response.series().get(1).value()).isEqualByComparingTo("24.00");
    assertThat(response.series().getLast().value()).isEqualByComparingTo("26.00");
    verify(cryptoHistoricalPricePort)
        .getHistoricalUsdPrices(
            org.mockito.ArgumentMatchers.eq("bitcoin"),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
    verify(cryptoHistoricalPricePort, never())
        .getHistoricalUsdPrices(
            org.mockito.ArgumentMatchers.eq("bitcoin"), org.mockito.ArgumentMatchers.anyInt());
  }

  @Test
  void shouldUseCoinGeckoMarketChartDaysForFixedPeriodsAndKeepAllUsingRange() {
    UUID userId = UUID.randomUUID();
    when(transactionHistoryPort.getTransactionsByUser(userId))
        .thenReturn(
            List.of(
                new PortfolioTransactionSnapshot(
                    "BTC",
                    "CRYPTO",
                    "BUY",
                    null,
                    new BigDecimal("2"),
                    new BigDecimal("20.00"),
                    new BigDecimal("10.00"),
                    BigDecimal.ZERO,
                    OffsetDateTime.of(2025, 4, 7, 0, 0, 0, 0, ZoneOffset.UTC))));
    when(assetCatalogQueryPort.findAssetIdBySymbol("BTC")).thenReturn(Optional.of("bitcoin"));
    when(cryptoHistoricalPricePort.getHistoricalUsdPrices("bitcoin", 30))
        .thenReturn(
            reactor.core.publisher.Mono.just(
                new CryptoHistoricalPriceSeries(
                    "bitcoin",
                    "usd",
                    List.of(
                        new CryptoHistoricalPricePoint(
                            Instant.parse("2025-04-07T00:00:00Z"), new BigDecimal("10.00")),
                        new CryptoHistoricalPricePoint(
                            Instant.parse("2025-04-08T00:00:00Z"), new BigDecimal("12.00"))))));
    when(assetPricePort.getCryptoPriceAmount("BTC"))
        .thenReturn(reactor.core.publisher.Mono.just(new BigDecimal("13.00")));

    PortfolioHoldingsPerformanceResponse response =
        portfolioService.getHoldingsPerformanceByPortfolioId(userId, "BTC", "30D");

    assertThat(response.series()).isNotEmpty();
    verify(cryptoHistoricalPricePort).getHistoricalUsdPrices("bitcoin", 30);
    verify(cryptoHistoricalPricePort, never())
        .getHistoricalUsdPrices(
            org.mockito.ArgumentMatchers.eq("bitcoin"),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any());
  }
}
