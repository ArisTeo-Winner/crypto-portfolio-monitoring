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

import com.mx.cryptomonitor.portfolio.application.port.out.PortfolioMarkersPort;
import com.mx.cryptomonitor.portfolio.application.port.out.PortfolioTransactionSnapshot;
import com.mx.cryptomonitor.portfolio.application.service.GetAssetMarkersService;
import com.mx.cryptomonitor.portfolio.domain.exception.PortfolioInvalidRequestException;
import com.mx.cryptomonitor.portfolio.domain.model.PortfolioMarker;

class GetAssetMarkersServiceTest {

  private static final UUID USER_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
  private static final Instant NOW = Instant.parse("2026-05-11T00:00:00Z");

  private final InMemoryPortfolioMarkersPort portfolioMarkersPort = new InMemoryPortfolioMarkersPort();
  private final GetAssetMarkersService service =
      new GetAssetMarkersService(portfolioMarkersPort, Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void buyGeneratesArrowUpMarker() {
    Instant time = Instant.parse("2026-05-10T12:00:00Z");
    portfolioMarkersPort.snapshots = List.of(snapshot("SOL", "BUY", "2.0", "154.46000000", time));

    List<PortfolioMarker> markers = service.getAssetMarkers(USER_ID, "SOL", "30d");

    assertThat(markers)
        .containsExactly(
            new PortfolioMarker(
                time.getEpochSecond(),
                "belowBar",
                "#16a34a",
                "arrowUp",
                "BUY 2 SOL @ 154.46"));
  }

  @Test
  void sellGeneratesArrowDownMarker() {
    Instant time = Instant.parse("2026-05-10T12:00:00Z");
    portfolioMarkersPort.snapshots = List.of(snapshot("SOL", "SELL", "0.5", "160.00000000", time));

    List<PortfolioMarker> markers = service.getAssetMarkers(USER_ID, "SOL", "30d");

    assertThat(markers)
        .containsExactly(
            new PortfolioMarker(
                time.getEpochSecond(),
                "aboveBar",
                "#ef4444",
                "arrowDown",
                "SELL 0.5 SOL @ 160"));
  }

  @Test
  void transferIsIgnored() {
    portfolioMarkersPort.snapshots =
        List.of(
            snapshot("SOL", "TRANSFER", "2.0", "154.46", Instant.parse("2026-05-10T12:00:00Z")),
            snapshot("SOL", "BUY", "1.0", "155.00", Instant.parse("2026-05-10T13:00:00Z")));

    assertThat(service.getAssetMarkers(USER_ID, "SOL", "30d"))
        .extracting(PortfolioMarker::text)
        .containsExactly("BUY 1 SOL @ 155");
  }

  @Test
  void nullTimestampIsIgnored() {
    portfolioMarkersPort.snapshots =
        List.of(
            snapshot("SOL", "BUY", "2.0", "154.46", null),
            snapshot("SOL", "SELL", "1.0", "160.00", Instant.parse("2026-05-10T13:00:00Z")));

    assertThat(service.getAssetMarkers(USER_ID, "SOL", "30d"))
        .extracting(PortfolioMarker::text)
        .containsExactly("SELL 1 SOL @ 160");
  }

  @Test
  void rangeFiltersCorrectly() {
    Instant boundary = NOW.minusSeconds(30L * 24L * 60L * 60L);
    portfolioMarkersPort.snapshots =
        List.of(
            snapshot("SOL", "BUY", "1.0", "100.00", boundary.minusSeconds(1)),
            snapshot("SOL", "BUY", "2.0", "101.00", boundary),
            snapshot("SOL", "SELL", "1.0", "102.00", NOW.minusSeconds(60)));

    assertThat(service.getAssetMarkers(USER_ID, "SOL", "30d"))
        .extracting(PortfolioMarker::text)
        .containsExactly("BUY 2 SOL @ 101", "SELL 1 SOL @ 102");
  }

  @Test
  void preservesStableOrderForSameTimestamp() {
    Instant sameTime = Instant.parse("2026-05-10T12:00:00Z");
    portfolioMarkersPort.snapshots =
        List.of(
            snapshot("SOL", "BUY", "1.0", "100.00", sameTime),
            snapshot("SOL", "SELL", "0.5", "101.00", sameTime),
            snapshot("SOL", "BUY", "2.0", "102.00", sameTime));

    assertThat(service.getAssetMarkers(USER_ID, "SOL", "30d"))
        .extracting(PortfolioMarker::text)
        .containsExactly("BUY 1 SOL @ 100", "SELL 0.5 SOL @ 101", "BUY 2 SOL @ 102");
  }

  @Test
  void returnsDeterministicResults() {
    portfolioMarkersPort.snapshots =
        List.of(
            snapshot("SOL", "BUY", "1.0", "100.00", Instant.parse("2026-05-10T12:00:00Z")),
            snapshot("SOL", "SELL", "0.5", "101.00", Instant.parse("2026-05-10T13:00:00Z")));

    List<PortfolioMarker> first = service.getAssetMarkers(USER_ID, "SOL", "30d");
    List<PortfolioMarker> second = service.getAssetMarkers(USER_ID, "SOL", "30d");

    assertThat(second).isEqualTo(first);
  }

  @Test
  void invalidRangeUsesPortfolioException() {
    assertThatThrownBy(() -> service.getAssetMarkers(USER_ID, "SOL", "180"))
        .isInstanceOf(PortfolioInvalidRequestException.class)
        .hasMessageContaining("range must be one of");
  }

  private PortfolioTransactionSnapshot snapshot(
      String symbol, String type, String quantity, String price, Instant time) {
    BigDecimal quantityValue = new BigDecimal(quantity);
    BigDecimal priceValue = new BigDecimal(price);
    return new PortfolioTransactionSnapshot(
        symbol,
        "CRYPTO",
        type,
        null,
        quantityValue,
        quantityValue.multiply(priceValue),
        priceValue,
        BigDecimal.ZERO,
        time == null ? null : LocalDateTime.ofInstant(time, ZoneOffset.UTC));
  }

  private static final class InMemoryPortfolioMarkersPort implements PortfolioMarkersPort {

    private List<PortfolioTransactionSnapshot> snapshots = List.of();

    @Override
    public List<PortfolioTransactionSnapshot> getTransactionsByUserAndSymbol(UUID userId, String symbol) {
      assertThat(userId).isEqualTo(USER_ID);
      assertThat(symbol).isEqualTo("SOL");
      return snapshots;
    }
  }
}
