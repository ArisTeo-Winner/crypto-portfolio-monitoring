package com.mx.cryptomonitor.integration.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import com.redis.testcontainers.RedisContainer;

@TestConfiguration
public class ContainersConfig {

  @Bean
  @ServiceConnection
  PostgreSQLContainer<?> postgres() {
    return new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));
  }

  @Bean
  @ServiceConnection
  RedisContainer redisContainer() {
    return new RedisContainer(DockerImageName.parse("redis:7")).withExposedPorts(6379);
  }
}
