package com.mx.cryptomonitor.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.mx.cryptomonitor.user.application.service.RefreshTokenStoreService;
import com.mx.cryptomonitor.user.domain.model.Session;
import com.mx.cryptomonitor.user.domain.model.User;
import com.mx.cryptomonitor.user.domain.repository.AuditLogRepository;
import com.mx.cryptomonitor.user.domain.repository.SessionRepository;
import com.mx.cryptomonitor.user.domain.repository.UserRepository;

import jakarta.servlet.http.Cookie;

// Keep this test focused on auth flows; lazy init avoids failing on unrelated bean wiring.
@SpringBootTest(properties = "spring.main.lazy-initialization=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class AuthRefreshTokenRotationIT {

  private static final Logger logger = LoggerFactory.getLogger(AuthRefreshTokenRotationIT.class);

  @Container
  static PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:16-alpine")
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
    registry.add("spring.cache.type", () -> "simple");
    registry.add("jwt.secret-base64", () -> "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
    registry.add("jwt.access-token-expiration", () -> "3600000");
    registry.add("jwt.refresh-token-expiration", () -> "604800000");

    logger.info("datasource.url:" + postgres.getJdbcUrl());
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository userRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private AuditLogRepository auditLogRepository;
  @Autowired private SessionRepository sessionRepository;
  @Autowired private RefreshTokenStoreService refreshTokenStoreService;

  @BeforeAll
  static void logContainerCoords() {
    System.out.printf(
        "TC host=%s port=%s jdbc=%s%n",
        postgres.getHost(), postgres.getMappedPort(5432), postgres.getJdbcUrl());
  }

  @BeforeEach
  void setUp() {
    auditLogRepository.deleteAllInBatch();
    sessionRepository.deleteAllInBatch();
    userRepository.deleteAllInBatch();

    User user =
        User.builder()
            .username("rotation-user")
            .email("rotation@example.com")
            .passwordHash(passwordEncoder.encode("ValidPass123!"))
            .active(true)
            .build();
    userRepository.save(user);
  }

  @Test
  void refreshShouldRotateTokenAndRejectReplayOfOldToken() throws Exception {
    MvcResult loginResult =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType("application/json")
                    .content("{\"email\":\"rotation@example.com\",\"password\":\"ValidPass123!\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andReturn();

    String oldRefreshToken = extractRefreshToken(loginResult);

    MvcResult refreshResult =
        mockMvc
            .perform(post("/api/v1/tokens/refresh").cookie(refreshCookie(oldRefreshToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andReturn();

    String newRefreshToken = extractRefreshToken(refreshResult);
    assertThat(newRefreshToken).isNotEqualTo(oldRefreshToken);

    RefreshTokenStoreService.StoredRefreshToken oldTokenEntity =
        refreshTokenStoreService
            .findByRawToken(oldRefreshToken)
            .orElseThrow(() -> new AssertionError("Old refresh token not found in persistence"));
    assertThat(oldTokenEntity.revoked()).isTrue();

    mockMvc
        .perform(post("/api/v1/tokens/refresh").cookie(refreshCookie(oldRefreshToken)))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
        .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
        .andExpect(jsonPath("$.detail").value("Authentication is required or token is invalid."));
  }

  @Test
  void logoutShouldRevokeRefreshTokenAndCloseSession() throws Exception {
    MvcResult loginResult =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType("application/json")
                    .content("{\"email\":\"rotation@example.com\",\"password\":\"ValidPass123!\"}"))
            .andExpect(status().isOk())
            .andReturn();

    String refreshToken = extractRefreshToken(loginResult);

    mockMvc
        .perform(post("/api/v1/auth/logout").cookie(refreshCookie(refreshToken)))
        .andExpect(status().isOk());

    RefreshTokenStoreService.StoredRefreshToken refreshTokenEntity =
        refreshTokenStoreService
            .findByRawToken(refreshToken)
            .orElseThrow(() -> new AssertionError("Refresh token should exist after logout"));

    assertThat(refreshTokenEntity.revoked()).isTrue();
    Optional<Session> sessionOpt = sessionRepository.findById(refreshTokenEntity.sessionId());
    assertThat(sessionOpt).isPresent();
    assertThat(sessionOpt.orElseThrow().isActive()).isFalse();
  }

  private Cookie refreshCookie(String refreshToken) {
    return new Cookie("refresh_token", refreshToken);
  }

  private String extractRefreshToken(MvcResult result) {
    String setCookie = result.getResponse().getHeader("Set-Cookie");
    assertThat(setCookie).contains("refresh_token=");
    return setCookie.split("refresh_token=")[1].split(";")[0];
  }
}
