package com.mx.cryptomonitor.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.connection.RedisConnectionFactory;

@SpringBootTest
class DockerConnectivityIT {

  private final Logger logger = LoggerFactory.getLogger(DockerConnectivityIT.class);

  @Autowired private DataSource dataSource;

  @Autowired private RedisConnectionFactory redisConnectionFactory;

  @Test
  void shouldConnectToPostgresInDockerCompose() throws Exception {

    logger.info("=== Ejecutando método shouldConnectToPostgresInDockerCompose() ===");

    try (Connection conn = dataSource.getConnection()) {
      assertThat(conn.isValid(2)).isTrue();
    }
  }

  @Test
  void shouldConnectToRedisInDockerCompose() {
    logger.info("=== Ejecutando método shouldConnectToRedisInDockerCompose()  ===");

    var conn = redisConnectionFactory.getConnection();
    conn.ping();
    assertThat(conn.ping()).isEqualTo("PONG");
    conn.close();
  }
}
