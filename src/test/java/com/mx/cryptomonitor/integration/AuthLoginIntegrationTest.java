package com.mx.cryptomonitor.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.mx.cryptomonitor.user.application.service.RefreshTokenStoreService;
import com.mx.cryptomonitor.user.domain.model.User;
import com.mx.cryptomonitor.user.domain.repository.AuditLogRepository;
import com.mx.cryptomonitor.user.domain.repository.SessionRepository;
import com.mx.cryptomonitor.user.domain.repository.UserRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class AuthLoginIntegrationTest {

  private static final org.slf4j.Logger logger =
      LoggerFactory.getLogger(AuthLoginIntegrationTest.class);
  @Autowired Environment env;
  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository userRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private SessionRepository sessionRepository;
  @Autowired private AuditLogRepository auditLogRepository;
  @MockBean private RefreshTokenStoreService refreshTokenStoreService;

  @Test
  void test() {

    String msgAssets = "Ejecutando Test AuthLoginIntegrationTest";

    logger.info("Mesanje de test:{}", msgAssets);
  }

  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:16")
          .withDatabaseName("testdb")
          .withUsername("test")
          .withPassword("test");

  @DynamicPropertySource
  static void registerProps(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
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

  @BeforeEach
  void setUp() {

    logger.info("spring.datasource.url=" + env.getProperty("spring.datasource.url"));
    logger.info("spring.datasource.username=" + env.getProperty("spring.datasource.username"));

    auditLogRepository.deleteAllInBatch();
    sessionRepository.deleteAllInBatch(); // si session referencia user o refresh_token
    // transactionRepository.deleteAllInBatch(); // si aplica
    // portfolioRepository.deleteAllInBatch();    // si aplica
    userRepository.deleteAllInBatch();

    User user =
        User.builder()
            .username("user1")
            .email("user@example.com")
            .passwordHash(passwordEncoder.encode("ValidPass123!"))
            .build();
    userRepository.save(user);

    when(refreshTokenStoreService.store(anyString(), any(), any(), any(), any(), any()))
        .thenReturn(
            new RefreshTokenStoreService.StoredRefreshToken(
                UUID.randomUUID(), user.getId(), UUID.randomUUID(), false));
  }

  @Test
  void login_ok_with_real_user_returns_tokens_without_jwt_cookie() throws Exception {
    var result =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType("application/json")
                    .content("{\"email\":\"user@example.com\",\"password\":\"ValidPass123!\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.refreshToken").doesNotExist())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(header().string("Pragma", "no-cache"))
            .andExpect(
                header()
                    .string("Set-Cookie", org.hamcrest.Matchers.containsString("refresh_token=")))
            .andExpect(
                header().string("Set-Cookie", org.hamcrest.Matchers.containsString("HttpOnly")))
            .andReturn();

    assertThat(result.getResponse().getCookie("jwt")).isNull();
  }

  @Test
  void login_bad_password_returns_401() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType("application/json")
                .content("{\"email\":\"user@example.com\",\"password\":\"WrongPass!\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void login_with_mixed_case_email_returns_tokens() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType("application/json")
                .content("{\"email\":\"User@Example.com\",\"password\":\"ValidPass123!\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").isNotEmpty())
        .andExpect(jsonPath("$.refreshToken").doesNotExist())
        .andExpect(
            header().string("Set-Cookie", org.hamcrest.Matchers.containsString("refresh_token=")))
        .andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("HttpOnly")));
  }
}
