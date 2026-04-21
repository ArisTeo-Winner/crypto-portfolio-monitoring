package com.mx.cryptomonitor.integration.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.mx.cryptomonitor.integration.support.InfraIntegrationTest;

@SpringBootTest
@ActiveProfiles("test")
class RedisCacheIT extends InfraIntegrationTest {

  private final Logger logger = LoggerFactory.getLogger(RedisCacheIT.class);

  @Autowired private RedisTemplate<String, String> redisTemplate;

  @Test
  void shouldCacheAndRetrieveValueFromRedis() {
    logger.info("=== Ejecutando método shouldCacheAndRetrieveValueFromRedis() ===");

    String key = "cache:test-key";
    String value = "Valor en Redis Global";

    redisTemplate.opsForValue().set(key, value);

    String retrieved = redisTemplate.opsForValue().get(key);
    assertThat(retrieved).isEqualTo(value);
  }
}
