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

import com.mx.cryptomonitor.portfolio.application.port.out.PortfolioMarkersPort;
import com.mx.cryptomonitor.portfolio.application.port.out.PortfolioTransactionSnapshot;
import com.mx.cryptomonitor.portfolio.application.service.GetAssetMarkersService;

class PortfolioAssetMarkersPerformanceTest {

  @Test
  void generatesOneThousandAssetMarkersUnderFiveHundredMilliseconds() {
    UUID userId = UUID.randomUUID();
    Instant now = Instant.parse("2026-05-11T00:00:00Z");
    List<PortfolioTransactionSnapshot> snapshots = new ArrayList<>();

    for (int index = 0; index < 1_000; index++) {
      snapshots.add(snapshot(index % 2 == 0 ? "BUY" : "SELL", now.minusSeconds(1_000L - index)));
    }

    GetAssetMarkersService service =
        new GetAssetMarkersService(
            new StaticPortfolioMarkersPort(snapshots), Clock.fixed(now, ZoneOffset.UTC));

    long started = System.nanoTime();
    assertThat(service.getAssetMarkers(userId, "SOL", "30d")).hasSize(1_000);
    Duration duration = Duration.ofNanos(System.nanoTime() - started);

    assertThat(duration).isLessThan(Duration.ofMillis(500));
  }

  private PortfolioTransactionSnapshot snapshot(String type, Instant time) {
    BigDecimal quantity = new BigDecimal("1.0");
    BigDecimal price = new BigDecimal("100.00");
    return new PortfolioTransactionSnapshot(
        "SOL",
        "CRYPTO",
        type,
        null,
        quantity,
        quantity.multiply(price),
        price,
        BigDecimal.ZERO,
        LocalDateTime.ofInstant(time, ZoneOffset.UTC));
  }

  private record StaticPortfolioMarkersPort(List<PortfolioTransactionSnapshot> snapshots)
      implements PortfolioMarkersPort {

    @Override
    public List<PortfolioTransactionSnapshot> getTransactionsByUserAndSymbol(
        UUID userId, String symbol) {
      return snapshots;
    }
  }
}
