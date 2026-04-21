package com.mx.cryptomonitor.integration.support;

import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.redis.testcontainers.RedisContainer;

@Testcontainers
public abstract class InfraIntegrationTest {

  @Container @ServiceConnection
  protected static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

  @Container @ServiceConnection
  protected static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7"));

  @BeforeAll
  static void logContainerCoords() {
    System.out.printf(
        "TC host=%s port=%s jdbc=%s%n",
        postgres.getHost(), postgres.getMappedPort(5432), postgres.getJdbcUrl());
  }
}
