package com.mx.cryptomonitor.integration.portfolio.infrastructure.outbound.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.mx.cryptomonitor.portfolio.application.port.out.PriceHistoryCachePort;
import com.mx.cryptomonitor.portfolio.domain.model.AssetType;
import com.mx.cryptomonitor.portfolio.infrastructure.outbound.redis.PriceHistoryRedisAdapter;

@Testcontainers(disabledWithoutDocker = true)
class PriceHistoryRedisAdapterIT {

  @Container
  static final GenericContainer<?> redis =
      new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

  private LettuceConnectionFactory connectionFactory;

  @AfterEach
  void cleanup() {
    if (connectionFactory != null) {
      connectionFactory.destroy();
    }
  }

  @Test
  void shouldStoreAndReadPriceHistoryUsingExpectedZsetKeyAndEpochSeconds() {
    StringRedisTemplate redisTemplate = redisTemplate();
    PriceHistoryRedisAdapter adapter = new PriceHistoryRedisAdapter(redisTemplate);
    Instant first = Instant.parse("2026-01-01T00:00:00Z");
    Instant second = Instant.parse("2026-01-02T00:00:00Z");

    adapter.storePriceHistory(
        AssetType.CRYPTO,
        "SOL",
        "180d",
        List.of(
            new PriceHistoryCachePort.PriceHistoryPoint(first, new BigDecimal("101.25")),
            new PriceHistoryCachePort.PriceHistoryPoint(second, new BigDecimal("102.50"))),
        Duration.ofHours(6));

    Set<ZSetOperations.TypedTuple<String>> tuples =
        redisTemplate.opsForZSet().rangeWithScores("price:CRYPTO:SOL:180d", 0, -1);

    assertThat(tuples).isNotNull();
    assertThat(tuples).hasSize(2);
    assertThat(tuples)
        .extracting(ZSetOperations.TypedTuple::getScore)
        .containsExactly(1.7672256E9, 1.767312E9);
    assertThat(adapter.getPriceHistory(AssetType.CRYPTO, "SOL", "180d"))
        .containsExactly(
            new PriceHistoryCachePort.PriceHistoryPoint(first, new BigDecimal("101.25")),
            new PriceHistoryCachePort.PriceHistoryPoint(second, new BigDecimal("102.50")));
    assertThat(redisTemplate.getExpire("price:CRYPTO:SOL:180d")).isPositive();
  }

  private StringRedisTemplate redisTemplate() {
    connectionFactory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
    connectionFactory.afterPropertiesSet();
    StringRedisTemplate template = new StringRedisTemplate(connectionFactory);
    template.afterPropertiesSet();
    template.getConnectionFactory().getConnection().serverCommands().flushAll();
    return template;
  }
}
