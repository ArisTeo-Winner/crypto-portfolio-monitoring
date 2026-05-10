package com.mx.cryptomonitor.portfolio.application.port.out;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PortfolioHistoryStorePort {

  void appendEquitySnapshot(UUID userId, Instant timestamp, BigDecimal totalPortfolioValue);

  List<PortfolioHistoryPoint> getEquityHistory(UUID userId, Instant fromInclusive);

  void trimEquityHistory(UUID userId, Instant beforeExclusive, Duration ttl);

  record PortfolioHistoryPoint(Instant timestamp, BigDecimal value) {}
}
