package com.mx.cryptomonitor.portfolio.application.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.mx.cryptomonitor.portfolio.application.port.out.PortfolioHistoryStorePort;
import com.mx.cryptomonitor.portfolio.domain.model.PortfolioEntry;
import com.mx.cryptomonitor.portfolio.domain.repository.PortfolioEntryRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class EquitySnapshotService {

  private static final int MONEY_SCALE = 2;
  private static final Duration EQUITY_TTL = Duration.ofDays(365);

  private final PortfolioEntryRepository portfolioEntryRepository;
  private final PortfolioHistoryStorePort portfolioHistoryStorePort;

  public void recordEquitySnapshot(UUID userId) {
    BigDecimal totalValue =
        portfolioEntryRepository.findByUserId(userId).stream()
            .map(PortfolioEntry::getCurrentValue)
            .map(value -> value != null ? value : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(MONEY_SCALE, RoundingMode.HALF_UP);

    Instant now = Instant.now();
    portfolioHistoryStorePort.appendEquitySnapshot(userId, now, totalValue);
    portfolioHistoryStorePort.trimEquityHistory(userId, now.minus(EQUITY_TTL), EQUITY_TTL);
  }

  public List<PortfolioHistoryStorePort.PortfolioHistoryPoint> getEquityHistory(
      UUID userId, int rangeDays) {
    Instant fromInclusive = Instant.now().minus(Duration.ofDays(rangeDays));
    return portfolioHistoryStorePort.getEquityHistory(userId, fromInclusive);
  }

  @Scheduled(fixedRateString = "${portfolio.equity.snapshot-fixed-rate-ms:300000}")
  public void recordEquitySnapshotsForActiveUsers() {
    for (UUID userId : portfolioEntryRepository.findDistinctUserIds()) {
      try {
        recordEquitySnapshot(userId);
      } catch (RuntimeException ex) {
        log.warn("Unable to record portfolio equity snapshot for user {}", userId, ex);
      }
    }
  }
}
