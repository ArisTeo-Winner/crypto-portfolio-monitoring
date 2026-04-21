package com.mx.cryptomonitor.integration.user.infrastructure.inbound.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.jayway.jsonpath.JsonPath;
import com.mx.cryptomonitor.CryptoPortfolioMonitoringApplication;
import com.mx.cryptomonitor.user.application.dto.request.UserRegistrationRequest;
import com.mx.cryptomonitor.user.domain.model.Role;
import com.mx.cryptomonitor.user.domain.repository.RoleRepository;

@SpringBootTest(
    classes = CryptoPortfolioMonitoringApplication.class,
    properties = "spring.main.lazy-initialization=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class TokenControllerIT {

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
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
    registry.add("spring.cache.type", () -> "simple");
    registry.add("jwt.secret-base64", () -> "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");
    registry.add("jwt.access-token-expiration", () -> "3600000");
    registry.add("jwt.refresh-token-expiration", () -> "604800000");
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private com.fasterxml.jackson.databind.ObjectMapper objectMapper;
  @Autowired private RoleRepository roleRepository;

  @BeforeAll
  static void logContainerCoords() {
    System.out.printf(
        "TC host=%s port=%s jdbc=%s%n",
        postgres.getHost(), postgres.getMappedPort(5432), postgres.getJdbcUrl());
  }

  @BeforeEach
  void ensureDefaultRoleExists() {
    roleRepository
        .findByName("ROLE_USER")
        .orElseGet(
            () ->
                roleRepository.save(
                    Role.builder()
                        .name("ROLE_USER")
                        .description("Default user role for integration tests")
                        .build()));
  }

  @Test
  void revoke_withValidAccessAndRefresh_thenRefreshMustBeRejected() throws Exception {
    Tokens tokens = registerAndLogin();

    mockMvc
        .perform(
            post("/api/v1/tokens/revoke")
                .header("Authorization", "Bearer " + tokens.accessToken())
                .header("X-Refresh-Token", tokens.refreshToken()))
        .andExpect(status().isNoContent());

    mockMvc
        .perform(
            post("/api/v1/tokens/refresh")
                .header("Authorization", "Bearer " + tokens.accessToken())
                .header("X-Refresh-Token", tokens.refreshToken()))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
        .andExpect(jsonPath("$.detail").value("Authentication is required or token is invalid."));
  }

  @Test
  void refresh_withValidAccessAndRefresh_returns_rotated_tokens() throws Exception {
    Tokens tokens = registerAndLogin();

    mockMvc
        .perform(
            post("/api/v1/tokens/refresh")
                .header("Authorization", "Bearer " + tokens.accessToken())
                .header("X-Refresh-Token", tokens.refreshToken()))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.accessToken").isNotEmpty())
        .andExpect(jsonPath("$.refreshToken").isNotEmpty());
  }

  @Test
  void revoke_withInvalidRefreshToken_andValidAccess_returns401() throws Exception {
    Tokens tokens = registerAndLogin();

    mockMvc
        .perform(
            post("/api/v1/tokens/revoke")
                .header("Authorization", "Bearer " + tokens.accessToken())
                .header("X-Refresh-Token", "invalid-refresh-token"))
        .andExpect(status().isUnauthorized())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
        .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
        .andExpect(jsonPath("$.detail").value("Authentication is required or token is invalid."));
  }

  @Test
  void refresh_withoutAuthorizationHeader_returns200_whenRefreshTokenIsValid() throws Exception {
    Tokens tokens = registerAndLogin();

    mockMvc
        .perform(post("/api/v1/tokens/refresh").header("X-Refresh-Token", tokens.refreshToken()))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(jsonPath("$.accessToken").isNotEmpty())
        .andExpect(jsonPath("$.refreshToken").isNotEmpty());
  }

  private Tokens registerAndLogin() throws Exception {
    String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    String username = "token_it_" + suffix;
    String email = "token_it_" + suffix + "@example.com";
    String password = "StrongP@ssw0rd!2026";

    UserRegistrationRequest registrationRequest =
        new UserRegistrationRequest(
            username, email, password, "Token", "Tester", null, null, null, null, null, null, null);

    mockMvc
        .perform(
            post("/api/v1/users/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registrationRequest)))
        .andExpect(status().isCreated());

    MvcResult loginResult =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            new com.mx.cryptomonitor.user.application.dto.request.LoginRequest(
                                email, password))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.refreshToken").isNotEmpty())
            .andReturn();

    String loginBody = loginResult.getResponse().getContentAsString();
    String accessToken = JsonPath.read(loginBody, "$.accessToken");
    String refreshToken = JsonPath.read(loginBody, "$.refreshToken");
    return new Tokens(accessToken, refreshToken);
  }

  private record Tokens(String accessToken, String refreshToken) {}
}
