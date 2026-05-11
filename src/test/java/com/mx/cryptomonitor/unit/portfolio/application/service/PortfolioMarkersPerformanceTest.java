package com.mx.cryptomonitor.unit.portfolio.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.mx.cryptomonitor.portfolio.application.port.out.PortfolioTransactionSnapshot;
import com.mx.cryptomonitor.portfolio.application.port.out.TransactionHistoryPort;
import com.mx.cryptomonitor.portfolio.application.service.GetPortfolioMarkersService;

class PortfolioMarkersPerformanceTest {

  @Test
  void generatesFiveThousandMarkersUnderOneSecond() {
    UUID userId = UUID.randomUUID();
    Instant now = Instant.parse("2026-05-10T00:00:00Z");
    List<PortfolioTransactionSnapshot> snapshots = new ArrayList<>();

    for (int index = 0; index < 5_000; index++) {
      snapshots.add(
          snapshot(
              "ASSET" + (index % 5),
              index % 2 == 0 ? "BUY" : "SELL",
              now.minusSeconds(5_000L - index)));
    }

    GetPortfolioMarkersService service =
        new GetPortfolioMarkersService(
            new StaticTransactionHistoryPort(snapshots), Clock.fixed(now, ZoneOffset.UTC));

    long started = System.nanoTime();
    assertThat(service.getMarkers(userId, "30d", "CRYPTO")).hasSize(5_000);
    Duration duration = Duration.ofNanos(System.nanoTime() - started);

    assertThat(duration).isLessThan(Duration.ofSeconds(1));
  }

  private PortfolioTransactionSnapshot snapshot(String symbol, String type, Instant time) {
    BigDecimal quantity = new BigDecimal("1.0");
    BigDecimal price = new BigDecimal("10.00");
    return new PortfolioTransactionSnapshot(
        symbol,
        "CRYPTO",
        type,
        null,
        quantity,
        quantity.multiply(price),
        price,
        BigDecimal.ZERO,
        LocalDateTime.ofInstant(time, ZoneOffset.UTC));
  }

  private record StaticTransactionHistoryPort(List<PortfolioTransactionSnapshot> snapshots)
      implements TransactionHistoryPort {

    @Override
    public List<PortfolioTransactionSnapshot> getTransactionsByUser(UUID userId) {
      return snapshots;
    }
  }
}
