package com.mx.cryptomonitor.unit.portfolio.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mx.cryptomonitor.portfolio.application.port.out.PortfolioTransactionSnapshot;
import com.mx.cryptomonitor.portfolio.application.port.out.TransactionHistoryPort;
import com.mx.cryptomonitor.portfolio.application.service.GetPortfolioMarkersService;
import com.mx.cryptomonitor.portfolio.domain.model.PortfolioMarker;

class GetPortfolioMarkersServiceTest {

  private static final UUID USER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
  private static final Instant NOW = Instant.parse("2026-05-10T00:00:00Z");

  private final InMemoryTransactionHistoryPort transactionHistoryPort =
      new InMemoryTransactionHistoryPort();
  private final GetPortfolioMarkersService service =
      new GetPortfolioMarkersService(
          transactionHistoryPort, Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void returnsSingleBuyMarker() {
    Instant time = Instant.parse("2026-05-09T12:00:00Z");
    transactionHistoryPort.snapshots =
        List.of(snapshot("BTC", "CRYPTO", "BUY", "1.0", time));

    List<PortfolioMarker> markers = service.getMarkers(USER_ID, "30d", null);

    assertThat(markers)
        .containsExactly(
            new PortfolioMarker(
                time.getEpochSecond(), "belowBar", "#22c55e", "arrowUp", "BUY 1 BTC"));
  }

  @Test
  void returnsSingleSellMarker() {
    Instant time = Instant.parse("2026-05-09T12:00:00Z");
    transactionHistoryPort.snapshots =
        List.of(snapshot("BTC", "CRYPTO", "SELL", "0.5", time));

    List<PortfolioMarker> markers = service.getMarkers(USER_ID, "30d", null);

    assertThat(markers)
        .containsExactly(
            new PortfolioMarker(
                time.getEpochSecond(), "aboveBar", "#ef4444", "arrowDown", "SELL 0.5 BTC"));
  }

  @Test
  void returnsBuyAndSellForSameAsset() {
    Instant buyTime = Instant.parse("2026-05-08T12:00:00Z");
    Instant sellTime = Instant.parse("2026-05-09T12:00:00Z");
    transactionHistoryPort.snapshots =
        List.of(
            snapshot("BTC", "CRYPTO", "BUY", "1.0", buyTime),
            snapshot("BTC", "CRYPTO", "SELL", "0.25", sellTime));

    List<PortfolioMarker> markers = service.getMarkers(USER_ID, "30d", null);

    assertThat(markers).extracting(PortfolioMarker::text).containsExactly("BUY 1 BTC", "SELL 0.25 BTC");
    assertThat(markers).extracting(PortfolioMarker::time).containsExactly(buyTime.getEpochSecond(), sellTime.getEpochSecond());
  }

  @Test
  void returnsMarkersForMultipleAssets() {
    transactionHistoryPort.snapshots =
        List.of(
            snapshot("BTC", "CRYPTO", "BUY", "1.0", Instant.parse("2026-05-08T12:00:00Z")),
            snapshot("AAPL", "STOCK", "BUY", "3.0", Instant.parse("2026-05-09T12:00:00Z")),
            snapshot("SPY", "ETF", "SELL", "2.0", Instant.parse("2026-05-09T13:00:00Z")));

    List<PortfolioMarker> markers = service.getMarkers(USER_ID, "30d", null);

    assertThat(markers)
        .extracting(PortfolioMarker::text)
        .containsExactly("BUY 1 BTC", "BUY 3 AAPL", "SELL 2 SPY");
  }

  @Test
  void preservesInputOrderForMultipleTransactionsAtSameTimestamp() {
    Instant sameTime = Instant.parse("2026-05-09T12:00:00Z");
    transactionHistoryPort.snapshots =
        List.of(
            snapshot("BTC", "CRYPTO", "BUY", "1.0", sameTime),
            snapshot("ETH", "CRYPTO", "SELL", "2.0", sameTime),
            snapshot("SOL", "CRYPTO", "BUY", "3.0", sameTime));

    List<PortfolioMarker> markers = service.getMarkers(USER_ID, "30d", null);

    assertThat(markers)
        .extracting(PortfolioMarker::text)
        .containsExactly("BUY 1 BTC", "SELL 2 ETH", "BUY 3 SOL");
    assertThat(markers).allMatch(marker -> marker.time() == sameTime.getEpochSecond());
  }

  @Test
  void filtersByRange() {
    Instant includedBoundary = NOW.minusSeconds(30L * 24L * 60L * 60L);
    Instant excluded = includedBoundary.minusSeconds(1);
    transactionHistoryPort.snapshots =
        List.of(
            snapshot("BTC", "CRYPTO", "BUY", "1.0", excluded),
            snapshot("ETH", "CRYPTO", "BUY", "2.0", includedBoundary),
            snapshot("SOL", "CRYPTO", "BUY", "3.0", NOW.minusSeconds(60)));

    List<PortfolioMarker> markers = service.getMarkers(USER_ID, "30d", null);

    assertThat(markers).extracting(PortfolioMarker::text).containsExactly("BUY 2 ETH", "BUY 3 SOL");
  }

  @Test
  void filtersByAssetTypes() {
    transactionHistoryPort.snapshots =
        List.of(
            snapshot("BTC", "CRYPTO", "BUY", "1.0", Instant.parse("2026-05-08T12:00:00Z")),
            snapshot("AAPL", "STOCK", "BUY", "3.0", Instant.parse("2026-05-09T12:00:00Z")),
            snapshot("SPY", "ETF", "BUY", "2.0", Instant.parse("2026-05-09T13:00:00Z")));

    List<PortfolioMarker> markers = service.getMarkers(USER_ID, "30d", "CRYPTO,STOCK");

    assertThat(markers).extracting(PortfolioMarker::text).containsExactly("BUY 1 BTC", "BUY 3 AAPL");
  }

  @Test
  void normalizesAssetTypesAndSymbols() {
    transactionHistoryPort.snapshots =
        List.of(
            snapshot(" btc ", " crypto ", " buy ", "1.0", Instant.parse("2026-05-08T12:00:00Z")),
            snapshot("aapl", "stock", "BUY", "3.0", Instant.parse("2026-05-09T12:00:00Z")));

    List<PortfolioMarker> markers = service.getMarkers(USER_ID, "30d", " crypto , stock ");

    assertThat(markers).extracting(PortfolioMarker::text).containsExactly("BUY 1 BTC", "BUY 3 AAPL");
  }

  @Test
  void ignoresTransactionsThatCannotBecomeChartMarkers() {
    transactionHistoryPort.snapshots =
        List.of(
            snapshot("BTC", "CRYPTO", "TRANSFER", "1.0", Instant.parse("2026-05-08T12:00:00Z")),
            snapshot("ETH", "CRYPTO", "BUY", "2.0", null),
            snapshot("SOL", "CRYPTO", "SELL", "3.0", Instant.parse("2026-05-09T12:00:00Z")));

    List<PortfolioMarker> markers = service.getMarkers(USER_ID, "30d", "CRYPTO");

    assertThat(markers).extracting(PortfolioMarker::text).containsExactly("SELL 3 SOL");
  }

  @Test
  void returnsEmptyWhenNoTransactionsMatch() {
    transactionHistoryPort.snapshots =
        List.of(snapshot("BTC", "CRYPTO", "BUY", "1.0", Instant.parse("2026-01-01T00:00:00Z")));

    assertThat(service.getMarkers(USER_ID, "30d", "STOCK")).isEmpty();
  }

  @Test
  void returnsDeterministicMarkersForRepeatedCalls() {
    transactionHistoryPort.snapshots =
        List.of(
            snapshot("BTC", "CRYPTO", "BUY", "1.0", Instant.parse("2026-05-08T12:00:00Z")),
            snapshot("ETH", "CRYPTO", "SELL", "0.5", Instant.parse("2026-05-09T12:00:00Z")));

    List<PortfolioMarker> first = service.getMarkers(USER_ID, "30d", "CRYPTO");
    List<PortfolioMarker> second = service.getMarkers(USER_ID, "30d", "CRYPTO");

    assertThat(second).isEqualTo(first);
  }

  @Test
  void rejectsInvalidRange() {
    assertThatThrownBy(() -> service.getMarkers(USER_ID, "13d", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("range must be one of");
  }

  @Test
  void rejectsInvalidAssetTypes() {
    assertThatThrownBy(() -> service.getMarkers(USER_ID, "30d", "CRYPTO,BOND"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private PortfolioTransactionSnapshot snapshot(
      String symbol, String assetType, String transactionType, String quantity, Instant time) {
    BigDecimal quantityValue = new BigDecimal(quantity);
    BigDecimal price = new BigDecimal("100.00");
    return new PortfolioTransactionSnapshot(
        symbol,
        assetType,
        transactionType,
        null,
        quantityValue,
        quantityValue.multiply(price),
        price,
        BigDecimal.ZERO,
        time == null ? null : LocalDateTime.ofInstant(time, ZoneOffset.UTC));
  }

  private static final class InMemoryTransactionHistoryPort implements TransactionHistoryPort {

    private List<PortfolioTransactionSnapshot> snapshots = List.of();

    @Override
    public List<PortfolioTransactionSnapshot> getTransactionsByUser(UUID userId) {
      assertThat(userId).isEqualTo(USER_ID);
      return snapshots;
    }
  }
}
