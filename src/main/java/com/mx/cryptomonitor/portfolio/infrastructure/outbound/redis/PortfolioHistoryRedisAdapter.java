package com.mx.cryptomonitor.portfolio.infrastructure.outbound.redis;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.mx.cryptomonitor.portfolio.application.port.out.PortfolioHistoryStorePort;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class PortfolioHistoryRedisAdapter implements PortfolioHistoryStorePort {

  private static final String KEY_PREFIX = "portfolio:equity:";

  private final StringRedisTemplate redisTemplate;

  @Override
  public void appendEquitySnapshot(UUID userId, Instant timestamp, BigDecimal totalPortfolioValue) {
    try {
      redisTemplate
          .opsForZSet()
          .add(key(userId), totalPortfolioValue.toPlainString(), timestamp.toEpochMilli());
    } catch (RuntimeException ex) {
      log.warn("Redis write failed for portfolio history user {}", userId, ex);
    }
  }

  @Override
  public List<PortfolioHistoryPoint> getEquityHistory(UUID userId, Instant fromInclusive) {
    try {
      return redisTemplate
          .opsForZSet()
          .rangeByScoreWithScores(key(userId), fromInclusive.toEpochMilli(), Double.MAX_VALUE)
          .stream()
          .filter(tuple -> tuple.getValue() != null && tuple.getScore() != null)
          .map(
              tuple ->
                  new PortfolioHistoryPoint(
                      Instant.ofEpochMilli(tuple.getScore().longValue()),
                      new BigDecimal(tuple.getValue())))
          .toList();
    } catch (RuntimeException ex) {
      log.warn("Redis read failed for portfolio history user {}", userId, ex);
      return List.of();
    }
  }

  @Override
  public void trimEquityHistory(UUID userId, Instant beforeExclusive, Duration ttl) {
    try {
      String redisKey = key(userId);
      redisTemplate
          .opsForZSet()
          .removeRangeByScore(redisKey, 0, beforeExclusive.toEpochMilli() - 1);
      redisTemplate.expire(redisKey, ttl);
    } catch (RuntimeException ex) {
      log.warn("Redis trim failed for portfolio history user {}", userId, ex);
    }
  }

  private String key(UUID userId) {
    return KEY_PREFIX + userId;
  }
}
