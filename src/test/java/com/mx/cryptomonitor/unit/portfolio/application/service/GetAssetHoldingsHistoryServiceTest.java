package com.mx.cryptomonitor.unit.portfolio.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.mx.cryptomonitor.portfolio.application.dto.response.AssetHoldingsHistoryResponse;
import com.mx.cryptomonitor.portfolio.application.port.out.AssetTransactionHistoryPort;
import com.mx.cryptomonitor.portfolio.application.port.out.MarketPriceHistoryPort;
import com.mx.cryptomonitor.portfolio.application.port.out.PortfolioTransactionSnapshot;
import com.mx.cryptomonitor.portfolio.application.service.GetAssetHoldingsHistoryService;
import com.mx.cryptomonitor.portfolio.domain.model.AssetType;
import com.mx.cryptomonitor.portfolio.domain.model.PricePoint;
import com.mx.cryptomonitor.portfolio.domain.model.TimeValuePoint;

class GetAssetHoldingsHistoryServiceTest {

  private final MarketPriceHistoryPort marketPriceHistoryPort =
      Mockito.mock(MarketPriceHistoryPort.class);
  private final AssetTransactionHistoryPort assetTransactionHistoryPort =
      Mockito.mock(AssetTransactionHistoryPort.class);
  private final GetAssetHoldingsHistoryService service =
      new GetAssetHoldingsHistoryService(marketPriceHistoryPort, assetTransactionHistoryPort);

  @Test
  void shouldBuildHoldingsSeriesAndMarkersFromTransactionsAndPriceHistory() {
    UUID userId = UUID.randomUUID();
    when(assetTransactionHistoryPort.getTransactionsByUserAndSymbol(userId, "BTC"))
        .thenReturn(
            List.of(
                snapshot("BUY", "1.0", "100.00", "2026-01-01T00:00:00"),
                snapshot("BUY", "0.5", "110.00", "2026-01-02T00:00:00"),
                snapshot("SELL", "0.75", "120.00", "2026-01-03T00:00:00")));
    when(marketPriceHistoryPort.getPriceHistory(AssetType.CRYPTO, "BTC", "30d"))
        .thenReturn(
            List.of(
                new PricePoint(Instant.parse("2026-01-01T00:00:00Z"), new BigDecimal("100.00")),
                new PricePoint(Instant.parse("2026-01-02T00:00:00Z"), new BigDecimal("110.00")),
                new PricePoint(Instant.parse("2026-01-03T00:00:00Z"), new BigDecimal("120.00")),
                new PricePoint(Instant.parse("2026-01-04T00:00:00Z"), new BigDecimal("130.00"))));

    AssetHoldingsHistoryResponse response = service.getAssetHoldingsHistory(userId, "BTC", "30d");

    assertThat(response.series())
        .containsExactly(
            new TimeValuePoint(1767225600L, new BigDecimal("100.00")),
            new TimeValuePoint(1767312000L, new BigDecimal("165.00")),
            new TimeValuePoint(1767398400L, new BigDecimal("90.00")),
            new TimeValuePoint(1767484800L, new BigDecimal("97.50")));
    assertThat(response.markers()).hasSize(3);
    assertThat(response.markers().getFirst().type()).isEqualTo("BUY");
    verify(marketPriceHistoryPort).getPriceHistory(AssetType.CRYPTO, "BTC", "30d");
  }

  @Test
  void shouldReturnEmptyResponseWhenUserHasNoTransactionsForAsset() {
    UUID userId = UUID.randomUUID();
    when(assetTransactionHistoryPort.getTransactionsByUserAndSymbol(userId, "BTC"))
        .thenReturn(List.of());

    AssetHoldingsHistoryResponse response = service.getAssetHoldingsHistory(userId, "BTC", "30d");

    assertThat(response.series()).isEmpty();
    assertThat(response.markers()).isEmpty();
  }

  @Test
  void shouldReturnDeterministicResponseForRepeatedCalls() {
    UUID userId = UUID.randomUUID();
    List<PortfolioTransactionSnapshot> snapshots =
        List.of(
            snapshot("BUY", "2", "10.00", "2026-01-01T00:00:00"),
            snapshot("SELL", "1", "12.00", "2026-01-02T00:00:00"));
    List<PricePoint> prices =
        List.of(
            new PricePoint(Instant.parse("2026-01-01T00:00:00Z"), new BigDecimal("10.00")),
            new PricePoint(Instant.parse("2026-01-02T00:00:00Z"), new BigDecimal("12.00")));
    when(assetTransactionHistoryPort.getTransactionsByUserAndSymbol(userId, "BTC"))
        .thenReturn(snapshots);
    when(marketPriceHistoryPort.getPriceHistory(AssetType.CRYPTO, "BTC", "30d")).thenReturn(prices);

    assertThat(service.getAssetHoldingsHistory(userId, "BTC", "30d"))
        .isEqualTo(service.getAssetHoldingsHistory(userId, "BTC", "30d"));
  }

  private PortfolioTransactionSnapshot snapshot(
      String type, String quantity, String pricePerUnit, String transactionDate) {
    return new PortfolioTransactionSnapshot(
        "BTC",
        "CRYPTO",
        type,
        null,
        new BigDecimal(quantity),
        new BigDecimal(quantity).multiply(new BigDecimal(pricePerUnit)),
        new BigDecimal(pricePerUnit),
        BigDecimal.ZERO,
        LocalDateTime.parse(transactionDate));
  }
}
