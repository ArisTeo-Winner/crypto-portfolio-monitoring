package com.mx.cryptomonitor.integration.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.mx.cryptomonitor.user.domain.model.User;
import com.mx.cryptomonitor.user.domain.repository.UserRepository;

import okhttp3.mockwebserver.MockWebServer;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@TestPropertySource(
    properties = {

      // application-test.properties
      // "spring.main.banner-mode=off",

      // Propiedades estáticas de prueba (serán la base; DynamicPropertySource las puede
      // sobrescribir)
      // "spring.jpa.hibernate.ddl-auto=update",
      // "spring.datasource.driver-class-name=org.postgresql.Driver",

      // Testcontainers: arranque paralelo
      "spring.testcontainers.beans.startup=parallel",

      // Permitir inyección de DynamicPropertyRegistry en métodos @Bean
      "spring.testcontainers.dynamic-property-registry-injection=allow",
    })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class InfraMultiServiceIntegrationTest {

  private static final Logger logger =
      LoggerFactory.getLogger(InfraMultiServiceIntegrationTest.class);

  @Autowired JdbcTemplate jdbc;

  @Autowired private UserRepository userRepository;

  static MockWebServer mockApi;

  @Container @ServiceConnection
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("testdb")
          .withUsername("testuser")
          .withPassword("testpass");

  @BeforeAll
  static void startMockApi() throws Exception {
    mockApi = new MockWebServer();
    mockApi.start();
    System.out.printf(
        "TC host=%s port=%s jdbc=%s%n",
        postgres.getHost(), postgres.getMappedPort(5432), postgres.getJdbcUrl());
  }

  @AfterAll
  static void stopMockApi() throws Exception {
    mockApi.shutdown();
  }

  @DynamicPropertySource
  static void dynamicProps(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", postgres::getJdbcUrl);
    r.add("spring.datasource.username", postgres::getUsername);
    r.add("spring.datasource.password", postgres::getPassword);
    r.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    r.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    r.add("spring.jpa.database-platform", () -> "org.hibernate.dialect.PostgreSQLDialect");
    r.add(
        "spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
    r.add("spring.flyway.enabled", () -> "false");
  }

  @Test
  void test() {

    logger.info("=== Ejecutando método test() desde InfraMultiServiceIntegrationTest ===");

    Integer one = jdbc.queryForObject("select 1", Integer.class);

    logger.info("spring.datasource.url: {}", jdbc.getDataSource());

    assertThat(one).isEqualTo(1);
  }

  @Test
  void save_and_find_ok() {
    var user = new User();
    user.setUsername("PostgreSQL");
    user.setEmail("test-pg@example.com");
    user.setFirstName("Usuario PostgreSQL");
    user.setLastName("SQL");

    var saved = userRepository.saveAndFlush(user);

    assertThat(saved.getId()).isNotNull();
  }
}
