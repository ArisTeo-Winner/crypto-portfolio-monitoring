package com.mx.cryptomonitor.unit.portfolio.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mx.cryptomonitor.marketdata.application.port.out.FxRateHistoryPort;
import com.mx.cryptomonitor.portfolio.application.port.out.MarketPriceHistoryPort;
import com.mx.cryptomonitor.portfolio.application.port.out.PortfolioAssetUniversePort;
import com.mx.cryptomonitor.portfolio.application.port.out.PortfolioAssetUniversePort.PortfolioAssetReference;
import com.mx.cryptomonitor.portfolio.application.port.out.PortfolioTransactionSnapshot;
import com.mx.cryptomonitor.portfolio.application.port.out.TransactionHistoryPort;
import com.mx.cryptomonitor.portfolio.application.service.GetPortfolioTotalHistoryService;
import com.mx.cryptomonitor.portfolio.domain.exception.MarketDataServerException;
import com.mx.cryptomonitor.portfolio.domain.model.AssetType;
import com.mx.cryptomonitor.portfolio.domain.model.ChartResolution;
import com.mx.cryptomonitor.portfolio.domain.model.PortfolioHistoryResult;
import com.mx.cryptomonitor.portfolio.domain.model.PricePoint;

class GetPortfolioTotalHistoryServiceTest {

  private final TransactionHistoryPort transactionHistoryPort =
      org.mockito.Mockito.mock(TransactionHistoryPort.class);
  private final MarketPriceHistoryPort marketPriceHistoryPort =
      org.mockito.Mockito.mock(MarketPriceHistoryPort.class);
  private final PortfolioAssetUniversePort portfolioAssetUniversePort =
      org.mockito.Mockito.mock(PortfolioAssetUniversePort.class);
  private final FxRateHistoryPort fxRateHistoryPort =
      org.mockito.Mockito.mock(FxRateHistoryPort.class);
  private final GetPortfolioTotalHistoryService service =
      new GetPortfolioTotalHistoryService(
          transactionHistoryPort,
          marketPriceHistoryPort,
          portfolioAssetUniversePort,
          fxRateHistoryPort);

  @Test
  void returnsZeroSeriesForKnownPortfolioAssetWithoutTransactionsWhenPricesExist() {
    UUID userId = UUID.randomUUID();
    when(transactionHistoryPort.getTransactionsByUser(userId)).thenReturn(List.of());
    when(portfolioAssetUniversePort.getAssetsByUser(userId))
        .thenReturn(List.of(new PortfolioAssetReference(AssetType.CRYPTO, "BTC")));
    // Return two price points within the actual requested range
    when(marketPriceHistoryPort.getPriceHistory(
            eq(AssetType.CRYPTO), eq("BTC"), any(ChartResolution.class)))
        .thenAnswer(
            inv -> {
              ChartResolution cr = inv.getArgument(2);
              long startSec = cr.start().getEpochSecond() - (cr.start().getEpochSecond() % 86400L);
              return List.of(
                  new PricePoint(Instant.ofEpochSecond(startSec), new BigDecimal("100.00")),
                  new PricePoint(
                      Instant.ofEpochSecond(startSec + 86400), new BigDecimal("110.00")));
            });

    PortfolioHistoryResult result = service.getTotalHistory(userId, "30d", "CRYPTO");

    // With no transactions, holdings = 0, so every value must be zero
    assertThat(result.series()).isNotEmpty();
    result.series().forEach(p -> assertThat(p.value()).isEqualByComparingTo(BigDecimal.ZERO));
  }

  @Test
  void returnsEmptySeriesForKnownPortfolioAssetWithoutTransactionsWhenPricesAreMissing() {
    UUID userId = UUID.randomUUID();
    when(transactionHistoryPort.getTransactionsByUser(userId)).thenReturn(List.of());
    when(portfolioAssetUniversePort.getAssetsByUser(userId))
        .thenReturn(List.of(new PortfolioAssetReference(AssetType.CRYPTO, "BTC")));
    when(marketPriceHistoryPort.getPriceHistory(
            eq(AssetType.CRYPTO), eq("BTC"), any(ChartResolution.class)))
        .thenReturn(List.of());

    assertThat(service.getTotalHistory(userId, "30d", "CRYPTO").series()).isEmpty();
  }

  @Test
  void skipsAssetWhoseProviderFailsAndMarksResponsePartial() {
    UUID userId = UUID.randomUUID();
    when(transactionHistoryPort.getTransactionsByUser(userId)).thenReturn(List.of());
    when(portfolioAssetUniversePort.getAssetsByUser(userId))
        .thenReturn(
            List.of(
                new PortfolioAssetReference(AssetType.CRYPTO, "HYPE"),
                new PortfolioAssetReference(AssetType.STOCK, "CRCL")));

    // HYPE (cripto): el proveedor falla -> no debe tumbar toda la gráfica.
    when(marketPriceHistoryPort.getPriceHistory(
            eq(AssetType.CRYPTO), eq("HYPE"), any(ChartResolution.class)))
        .thenThrow(new MarketDataServerException("{\"status\":404,\"error\":\"Not Found\"}"));
    // CRCL (stock): responde con datos.
    when(marketPriceHistoryPort.getPriceHistory(
            eq(AssetType.STOCK), eq("CRCL"), any(ChartResolution.class)))
        .thenAnswer(
            inv -> {
              ChartResolution cr = inv.getArgument(2);
              long startSec = cr.start().getEpochSecond() - (cr.start().getEpochSecond() % 86400L);
              return List.of(
                  new PricePoint(Instant.ofEpochSecond(startSec), new BigDecimal("100.00")),
                  new PricePoint(
                      Instant.ofEpochSecond(startSec + 86400), new BigDecimal("110.00")));
            });

    PortfolioHistoryResult result = service.getTotalHistory(userId, "1M", "CRYPTO,STOCK");

    // Sin 502: se omite HYPE, se conserva CRCL y se marca la respuesta como parcial.
    assertThat(result.partial()).isTrue();
    assertThat(result.unavailableSymbols()).containsExactly("HYPE");
    assertThat(result.series()).isNotEmpty();
  }

  @Test
  void normalizesMxnBuyCostToUsdBaseInReturnMetrics() {
    UUID userId = UUID.randomUUID();
    OffsetDateTime buyDate = OffsetDateTime.now(ZoneOffset.UTC).minusDays(10);
    PortfolioTransactionSnapshot mxnBuy =
        new PortfolioTransactionSnapshot(
            "MELI",
            "STOCK",
            "BUY",
            null,
            new BigDecimal("1"),
            new BigDecimal("31562.38"),
            new BigDecimal("31562.38"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            buyDate,
            "MXN");
    when(transactionHistoryPort.getTransactionsByUser(userId)).thenReturn(List.of(mxnBuy));
    when(portfolioAssetUniversePort.getAssetsByUser(userId)).thenReturn(List.of());
    when(fxRateHistoryPort.usdMxnRateOn(any())).thenReturn(Optional.of(new BigDecimal("17.5147")));
    when(marketPriceHistoryPort.getPriceHistory(
            eq(AssetType.STOCK), eq("MELI"), any(ChartResolution.class)))
        .thenAnswer(
            inv -> {
              ChartResolution cr = inv.getArgument(2);
              long startSec = cr.start().getEpochSecond() - (cr.start().getEpochSecond() % 86400L);
              return List.of(
                  new PricePoint(Instant.ofEpochSecond(startSec), new BigDecimal("1801.22")),
                  new PricePoint(
                      Instant.ofEpochSecond(startSec + 86400), new BigDecimal("1801.22")));
            });

    PortfolioHistoryResult result = service.getTotalHistory(userId, "30d", "STOCK");

    assertThat(result.returnMetrics()).isNotNull();
    // 31562.38 MXN / 17.5147 = 1802.05 USD (base), no el monto crudo en pesos.
    assertThat(result.returnMetrics().totalInvested())
        .isEqualByComparingTo(new BigDecimal("1802.05"));
  }
}
