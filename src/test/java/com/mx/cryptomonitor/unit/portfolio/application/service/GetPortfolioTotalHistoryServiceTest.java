package com.mx.cryptomonitor.unit.portfolio.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mx.cryptomonitor.portfolio.application.port.out.MarketPriceHistoryPort;
import com.mx.cryptomonitor.portfolio.application.port.out.PortfolioAssetUniversePort;
import com.mx.cryptomonitor.portfolio.application.port.out.PortfolioAssetUniversePort.PortfolioAssetReference;
import com.mx.cryptomonitor.portfolio.application.port.out.TransactionHistoryPort;
import com.mx.cryptomonitor.portfolio.application.service.GetPortfolioTotalHistoryService;
import com.mx.cryptomonitor.portfolio.domain.model.AssetType;
import com.mx.cryptomonitor.portfolio.domain.model.PricePoint;
import com.mx.cryptomonitor.portfolio.domain.model.TimeValuePoint;

class GetPortfolioTotalHistoryServiceTest {

  private final TransactionHistoryPort transactionHistoryPort =
      org.mockito.Mockito.mock(TransactionHistoryPort.class);
  private final MarketPriceHistoryPort marketPriceHistoryPort =
      org.mockito.Mockito.mock(MarketPriceHistoryPort.class);
  private final PortfolioAssetUniversePort portfolioAssetUniversePort =
      org.mockito.Mockito.mock(PortfolioAssetUniversePort.class);
  private final GetPortfolioTotalHistoryService service =
      new GetPortfolioTotalHistoryService(
          transactionHistoryPort, marketPriceHistoryPort, portfolioAssetUniversePort);

  @Test
  void returnsZeroSeriesForKnownPortfolioAssetWithoutTransactionsWhenPricesExist() {
    UUID userId = UUID.randomUUID();
    when(transactionHistoryPort.getTransactionsByUser(userId)).thenReturn(List.of());
    when(portfolioAssetUniversePort.getAssetsByUser(userId))
        .thenReturn(List.of(new PortfolioAssetReference(AssetType.CRYPTO, "BTC")));
    when(marketPriceHistoryPort.getPriceHistory(AssetType.CRYPTO, "BTC", "30d"))
        .thenReturn(
            List.of(
                new PricePoint(Instant.parse("2026-01-01T00:00:00Z"), new BigDecimal("100.00")),
                new PricePoint(Instant.parse("2026-01-02T00:00:00Z"), new BigDecimal("110.00"))));

    List<TimeValuePoint> result = service.getTotalHistory(userId, "30d", "CRYPTO");

    assertThat(result)
        .containsExactly(
            new TimeValuePoint(1767225600L, new BigDecimal("0.00")),
            new TimeValuePoint(1767312000L, new BigDecimal("0.00")));
  }

  @Test
  void returnsEmptyForKnownPortfolioAssetWithoutTransactionsWhenPricesAreMissing() {
    UUID userId = UUID.randomUUID();
    when(transactionHistoryPort.getTransactionsByUser(userId)).thenReturn(List.of());
    when(portfolioAssetUniversePort.getAssetsByUser(userId))
        .thenReturn(List.of(new PortfolioAssetReference(AssetType.CRYPTO, "BTC")));
    when(marketPriceHistoryPort.getPriceHistory(AssetType.CRYPTO, "BTC", "30d"))
        .thenReturn(List.of());

    assertThat(service.getTotalHistory(userId, "30d", "CRYPTO")).isEmpty();
  }
}
