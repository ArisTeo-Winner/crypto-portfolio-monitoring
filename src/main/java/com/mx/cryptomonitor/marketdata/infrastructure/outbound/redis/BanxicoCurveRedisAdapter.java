package com.mx.cryptomonitor.marketdata.infrastructure.outbound.redis;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.mx.cryptomonitor.marketdata.application.port.out.BanxicoCurveCachePort;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class BanxicoCurveRedisAdapter implements BanxicoCurveCachePort {

  private static final String CURVE_KEY = "banxico:cetes:curve";
  private static final String AUCTION_DATE_KEY = "banxico:cetes:auctionDate";
  private static final Duration CACHE_TTL = Duration.ofDays(8);

  private final StringRedisTemplate redisTemplate;

  @Override
  public Map<Integer, BigDecimal> readCurve() {
    Map<Object, Object> entries = redisTemplate.opsForHash().entries(CURVE_KEY);
    if (entries.isEmpty()) {
      return Map.of();
    }
    Map<Integer, BigDecimal> curve = new TreeMap<>();
    entries.forEach(
        (term, rate) -> curve.put(Integer.valueOf((String) term), new BigDecimal((String) rate)));
    return curve;
  }

  @Override
  public void writeCurve(Map<Integer, BigDecimal> curve, LocalDate auctionDate) {
    Map<String, String> fields = new TreeMap<>();
    curve.forEach((term, rate) -> fields.put(String.valueOf(term), rate.toPlainString()));
    redisTemplate.opsForHash().putAll(CURVE_KEY, fields);
    redisTemplate.expire(CURVE_KEY, CACHE_TTL);
    redisTemplate.opsForValue().set(AUCTION_DATE_KEY, auctionDate.toString(), CACHE_TTL);
  }

  @Override
  public Optional<LocalDate> readAuctionDate() {
    String stored = redisTemplate.opsForValue().get(AUCTION_DATE_KEY);
    return Optional.ofNullable(stored).map(LocalDate::parse);
  }
}
