package com.mx.cryptomonitor.integration.support;

import org.junit.jupiter.api.BeforeAll;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.redis.testcontainers.RedisContainer;

@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class InfraIntegrationTest {

  @Container @ServiceConnection
  protected static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>(DockerImageName.parse("postgres:16-alpine"));

  @Container @ServiceConnection
  protected static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7"));

  @DynamicPropertySource
  static void registerInfrastructureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    registry.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
    registry.add(
        "spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    registry.add("spring.flyway.enabled", () -> "false");
  }

  @BeforeAll
  static void logContainerCoords() {
    System.out.printf(
        "TC host=%s port=%s jdbc=%s%n",
        postgres.getHost(), postgres.getMappedPort(5432), postgres.getJdbcUrl());
  }
}
