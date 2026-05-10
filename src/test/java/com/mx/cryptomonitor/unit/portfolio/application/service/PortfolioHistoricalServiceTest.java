package com.mx.cryptomonitor.unit.portfolio.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.mx.cryptomonitor.portfolio.application.port.out.PortfolioHistoryStorePort;
import com.mx.cryptomonitor.portfolio.application.port.out.TransactionHistoryPort;
import com.mx.cryptomonitor.portfolio.application.service.EquitySnapshotService;
import com.mx.cryptomonitor.portfolio.application.service.PortfolioHistoricalService;
import com.mx.cryptomonitor.portfolio.application.service.PortfolioRangeValidator;
import com.mx.cryptomonitor.portfolio.application.service.RealizedPnLService;

class PortfolioHistoricalServiceTest {

  @Test
  void downsampleKeepsSeriesBoundedAndPreservesLastPoint() {
    PortfolioHistoricalService service =
        new PortfolioHistoricalService(
            mock(EquitySnapshotService.class),
            mock(TransactionHistoryPort.class),
            new RealizedPnLService(),
            new PortfolioRangeValidator());
    List<PortfolioHistoryStorePort.PortfolioHistoryPoint> points = new ArrayList<>();
    Instant start = Instant.parse("2026-01-01T00:00:00Z");
    for (int index = 0; index < 1200; index++) {
      points.add(
          new PortfolioHistoryStorePort.PortfolioHistoryPoint(
              start.plusSeconds(index), BigDecimal.valueOf(index)));
    }

    List<PortfolioHistoryStorePort.PortfolioHistoryPoint> sampled = service.downsample(points);

    assertThat(sampled).hasSizeLessThanOrEqualTo(501);
    assertThat(sampled.get(sampled.size() - 1).timestamp()).isEqualTo(points.get(points.size() - 1).timestamp());
  }

  @Test
  void rangeValidatorRejectsInvalidRanges() {
    PortfolioRangeValidator validator = new PortfolioRangeValidator();

    assertThat(validator.validate("180")).isEqualTo(180);
    assertThatThrownBy(() -> validator.validate("0")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> validator.validate("30d")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> validator.validate("366")).isInstanceOf(IllegalArgumentException.class);
  }
}
