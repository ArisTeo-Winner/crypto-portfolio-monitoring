package com.mx.cryptomonitor.portfolio.infrastructure.outbound.redis;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Component;

import com.mx.cryptomonitor.portfolio.application.port.out.PriceHistoryCachePort;
import com.mx.cryptomonitor.portfolio.domain.model.AssetType;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class PriceHistoryRedisAdapter implements PriceHistoryCachePort {

  private final StringRedisTemplate redisTemplate;

  @Override
  public void storePriceHistory(
      AssetType assetType,
      String symbol,
      String range,
      List<PriceHistoryPoint> points,
      Duration ttl) {
    try {
      String key = key(assetType, symbol, range);
      redisTemplate.delete(key);
      for (PriceHistoryPoint point : points) {
        redisTemplate
            .opsForZSet()
            .add(key, point.price().toPlainString(), point.time().getEpochSecond());
      }
      redisTemplate.expire(key, ttl);
    } catch (RuntimeException ex) {
      log.warn("Redis price history write failed for {} {} {}", assetType, symbol, range, ex);
    }
  }

  @Override
  public List<PriceHistoryPoint> getPriceHistory(AssetType assetType, String symbol, String range) {
    try {
      Set<ZSetOperations.TypedTuple<String>> tuples =
          redisTemplate.opsForZSet().rangeWithScores(key(assetType, symbol, range), 0, -1);
      if (tuples == null) {
        return List.of();
      }
      return tuples.stream()
          .filter(tuple -> tuple.getValue() != null && tuple.getScore() != null)
          .map(
              tuple ->
                  new PriceHistoryPoint(
                      Instant.ofEpochSecond(tuple.getScore().longValue()),
                      new BigDecimal(tuple.getValue())))
          .toList();
    } catch (RuntimeException ex) {
      log.warn("Redis price history read failed for {} {} {}", assetType, symbol, range, ex);
      return List.of();
    }
  }

  @Override
  public boolean acquireLoadLock(AssetType assetType, String symbol, String range, Duration ttl) {
    try {
      Boolean acquired =
          redisTemplate.opsForValue().setIfAbsent(lockKey(assetType, symbol, range), "1", ttl);
      return Boolean.TRUE.equals(acquired);
    } catch (RuntimeException ex) {
      log.warn("Redis price history lock failed for {} {} {}", assetType, symbol, range, ex);
      return true;
    }
  }

  @Override
  public void releaseLoadLock(AssetType assetType, String symbol, String range) {
    try {
      redisTemplate.delete(lockKey(assetType, symbol, range));
    } catch (RuntimeException ex) {
      log.debug(
          "Redis price history lock release failed for {} {} {}", assetType, symbol, range, ex);
    }
  }

  private String key(AssetType assetType, String symbol, String range) {
    return "price:"
        + assetType.name()
        + ":"
        + symbol.trim().toUpperCase()
        + ":"
        + range.trim().toLowerCase();
  }

  private String lockKey(AssetType assetType, String symbol, String range) {
    return key(assetType, symbol, range) + ":lock";
  }
}
