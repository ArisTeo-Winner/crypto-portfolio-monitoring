package com.mx.cryptomonitor.integration.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.mx.cryptomonitor.shared.infrastructure.security.ratelimit.RedisRateLimitStore;

@Testcontainers(disabledWithoutDocker = true)
class RedisRateLimitStoreIT {

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
  void shouldShareRateLimitStateAcrossStoreInstances() {
    StringRedisTemplate template = redisTemplate();
    RedisRateLimitStore storeOne = RedisRateLimitStore.forTests(template, true);
    RedisRateLimitStore storeTwo = RedisRateLimitStore.forTests(template, true);

    assertThat(storeOne.consumeFixedWindow("asset-search", "client-a", 2, 60)).isZero();
    assertThat(storeTwo.consumeFixedWindow("asset-search", "client-a", 2, 60)).isZero();
    assertThat(storeOne.consumeFixedWindow("asset-search", "client-a", 2, 60)).isGreaterThan(0L);
  }

  @Test
  void shouldPropagateBlockWindowAcrossStoreInstances() {
    StringRedisTemplate template = redisTemplate();
    RedisRateLimitStore storeOne = RedisRateLimitStore.forTests(template, true);
    RedisRateLimitStore storeTwo = RedisRateLimitStore.forTests(template, true);

    assertThat(storeOne.consumeFixedWindowWithBlock("login", "client-b", 2, 60, 120)).isZero();
    assertThat(storeOne.consumeFixedWindowWithBlock("login", "client-b", 2, 60, 120)).isZero();
    assertThat(storeTwo.consumeFixedWindowWithBlock("login", "client-b", 2, 60, 120))
        .isGreaterThanOrEqualTo(120L);
    assertThat(storeOne.consumeFixedWindowWithBlock("login", "client-b", 2, 60, 120))
        .isGreaterThan(0L);
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
